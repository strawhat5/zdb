/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.message;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.state.ZbColumnFamilies;
import org.agrona.DirectBuffer;

public final class MessageSubscriptionState {

  private final DbContext dbContext;

  // (elementInstanceKey, messageName) => MessageSubscription
  private final DbLong elementInstanceKey;
  private final DbString messageName;
  private final MessageSubscription messageSubscription;
  private final DbCompositeKey<DbLong, DbString> elementKeyAndMessageName;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, MessageSubscription>
      subscriptionColumnFamily;

  // (sentTime, elementInstanceKey, messageName) => \0
  private final DbLong sentTime;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>> sentTimeCompositeKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>, DbNil>
      sentTimeColumnFamily;

  // (messageName, correlationKey, elementInstanceKey) => \0
  private final DbString correlationKey;
  private final DbCompositeKey<DbString, DbString> nameAndCorrelationKey;
  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>
      nameCorrelationAndElementInstanceKey;
  private final ColumnFamily<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>, DbNil>
      messageNameAndCorrelationKeyColumnFamily;

  public MessageSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final DbContext dbContext) {
    this.dbContext = dbContext;

    elementInstanceKey = new DbLong();
    messageName = new DbString();
    messageSubscription = new MessageSubscription();
    elementKeyAndMessageName = new DbCompositeKey<>(elementInstanceKey, messageName);
    subscriptionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_KEY,
            dbContext,
            elementKeyAndMessageName,
            messageSubscription);

    sentTime = new DbLong();
    sentTimeCompositeKey = new DbCompositeKey<>(sentTime, elementKeyAndMessageName);
    sentTimeColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_SENT_TIME,
            dbContext,
            sentTimeCompositeKey,
            DbNil.INSTANCE);

    correlationKey = new DbString();
    nameAndCorrelationKey = new DbCompositeKey<>(messageName, correlationKey);
    nameCorrelationAndElementInstanceKey =
        new DbCompositeKey<>(nameAndCorrelationKey, elementInstanceKey);
    messageNameAndCorrelationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY,
            dbContext,
            nameCorrelationAndElementInstanceKey,
            DbNil.INSTANCE);
  }

  public MessageSubscription get(final long elementInstanceKey, final DirectBuffer messageName) {
    this.messageName.wrapBuffer(messageName);
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    return subscriptionColumnFamily.get(elementKeyAndMessageName);
  }

  public void visitSubscriptions(
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final MessageSubscriptionVisitor visitor) {

    this.messageName.wrapBuffer(messageName);
    this.correlationKey.wrapBuffer(correlationKey);

    messageNameAndCorrelationKeyColumnFamily.whileEqualPrefix(
        nameAndCorrelationKey,
        (compositeKey, nil) -> {
          return visitMessageSubscription(elementKeyAndMessageName, visitor);
        });
  }

  private Boolean visitMessageSubscription(
      final DbCompositeKey<DbLong, DbString> elementKeyAndMessageName,
      final MessageSubscriptionVisitor visitor) {
    final MessageSubscription messageSubscription =
        subscriptionColumnFamily.get(elementKeyAndMessageName);

    if (messageSubscription == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to find subscription with key %d and %s, but no subscription found",
              elementKeyAndMessageName.getFirst().getValue(),
              elementKeyAndMessageName.getSecond()));
    }
    return visitor.visit(messageSubscription);
  }

  public void visitSubscriptionBefore(
      final long deadline, final MessageSubscriptionVisitor visitor) {
    sentTimeColumnFamily.whileTrue(
        (compositeKey, nil) -> {
          final long sentTime = compositeKey.getFirst().getValue();
          if (sentTime < deadline) {
            return visitMessageSubscription(compositeKey.getSecond(), visitor);
          }
          return false;
        });
  }

  public boolean existSubscriptionForElementInstance(
      final long elementInstanceKey, final DirectBuffer messageName) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);

    return subscriptionColumnFamily.exists(elementKeyAndMessageName);
  }

  @FunctionalInterface
  public interface MessageSubscriptionVisitor {
    boolean visit(MessageSubscription subscription);
  }
}
