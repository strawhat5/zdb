/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.zdb.impl.db;

import io.zeebe.db.DbContext;
import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.db.TransactionOperation;
import io.zeebe.db.ZeebeDbException;
import io.zeebe.db.ZeebeDbTransaction;
import io.zeebe.util.exception.RecoverableException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

public final class ReadOnlyContext implements DbContext {
  private static final byte[] ZERO_SIZE_ARRAY = new byte[0];

  // we can also simply use one buffer
  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();

  private final DirectBuffer keyViewBuffer = new UnsafeBuffer(0, 0);
  private final DirectBuffer valueViewBuffer = new UnsafeBuffer(0, 0);

  private final Queue<ExpandableArrayBuffer> prefixKeyBuffers;

  private final RocksDB db;

  ReadOnlyContext(final RocksDB db) {
    this.db = db;
    prefixKeyBuffers = new ArrayDeque<>();
    prefixKeyBuffers.add(new ExpandableArrayBuffer());
    prefixKeyBuffers.add(new ExpandableArrayBuffer());
  }

  @Override
  public void writeKey(final DbKey key) {
    key.write(keyBuffer, 0);
  }

  @Override
  public byte[] getKeyBufferArray() {
    return keyBuffer.byteArray();
  }

  @Override
  public void writeValue(final DbValue value) {
    value.write(valueBuffer, 0);
  }

  @Override
  public byte[] getValueBufferArray() {
    return valueBuffer.byteArray();
  }

  @Override
  public void wrapKeyView(final byte[] key) {
    if (key != null) {
      keyViewBuffer.wrap(key);
    } else {
      keyViewBuffer.wrap(ZERO_SIZE_ARRAY);
    }
  }

  @Override
  public DirectBuffer getKeyView() {
    return isKeyViewEmpty() ? null : keyViewBuffer;
  }

  @Override
  public boolean isKeyViewEmpty() {
    return keyViewBuffer.capacity() == ZERO_SIZE_ARRAY.length;
  }

  @Override
  public void wrapValueView(final byte[] value) {
    if (value != null) {
      valueViewBuffer.wrap(value);
    } else {
      valueViewBuffer.wrap(ZERO_SIZE_ARRAY);
    }
  }

  @Override
  public DirectBuffer getValueView() {
    return isValueViewEmpty() ? null : valueViewBuffer;
  }

  @Override
  public boolean isValueViewEmpty() {
    return valueViewBuffer.capacity() == ZERO_SIZE_ARRAY.length;
  }

  @Override
  public void withPrefixKeyBuffer(final Consumer<ExpandableArrayBuffer> prefixKeyBufferConsumer) {
    if (prefixKeyBuffers.peek() == null) {
      throw new IllegalStateException(
          "Currently nested prefix iterations are not supported! This will cause unexpected behavior.");
    }
    final ExpandableArrayBuffer prefixKeyBuffer = prefixKeyBuffers.remove();
    try {
      prefixKeyBufferConsumer.accept(prefixKeyBuffer);
    } finally {
      prefixKeyBuffers.add(prefixKeyBuffer);
    }
  }

  @Override
  public RocksIterator newIterator(final ReadOptions options, final ColumnFamilyHandle handle) {
    return db.newIterator(handle, options);
  }

  @Override
  public void runInTransaction(final TransactionOperation operations) {
    try {
      operations.run();
    } catch (final RecoverableException recoverableException) {
      throw recoverableException;
    } catch (final RocksDBException rdbex) {
      final String errorMessage = "Unexpected error occurred during RocksDB transaction.";
      if (isRocksDbExceptionRecoverable(rdbex)) {
        throw new ZeebeDbException(errorMessage, rdbex);
      } else {
        throw new RuntimeException(errorMessage, rdbex);
      }
    } catch (final Exception ex) {
      throw new RuntimeException(
          "Unexpected error occurred during zeebe db transaction operation.", ex);
    }
  }

  @Override
  public ZeebeDbTransaction getCurrentTransaction() {
    return null;
  }

  private boolean isRocksDbExceptionRecoverable(final RocksDBException rdbex) {
    return false;
  }
}
