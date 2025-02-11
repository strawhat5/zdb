/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import io.zeebe.engine.processing.bpmn.WorkflowInstanceLifecycle;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.ObjectProperty;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public final class ElementInstance extends UnpackedObject implements DbValue {

  private final LongProperty parentKeyProp = new LongProperty("parentKey", -1L);
  private final IntegerProperty childCountProp = new IntegerProperty("childCount", 0);
  private final LongProperty jobKeyProp = new LongProperty("jobKey", 0L);
  private final IntegerProperty activeTokensProp = new IntegerProperty("activeTokens", 0);
  private final IntegerProperty multiInstanceLoopCounterProp =
      new IntegerProperty("multiInstanceLoopCounter", 0);
  private final LongProperty interruptingEventKeyProp =
      new LongProperty("interruptingEventKey", -1L);
  private final LongProperty calledChildInstanceKeyProp =
      new LongProperty("calledChildInstanceKey", -1L);
  private final ObjectProperty<IndexedRecord> recordProp =
      new ObjectProperty<>("elementRecord", new IndexedRecord());

  public ElementInstance() {
    declareProperty(parentKeyProp)
        .declareProperty(childCountProp)
        .declareProperty(jobKeyProp)
        .declareProperty(activeTokensProp)
        .declareProperty(multiInstanceLoopCounterProp)
        .declareProperty(interruptingEventKeyProp)
        .declareProperty(calledChildInstanceKeyProp)
        .declareProperty(recordProp);
  }

  public ElementInstance(
      final long key,
      final ElementInstance parent,
      final WorkflowInstanceIntent state,
      final WorkflowInstanceRecord value) {
    this();

    recordProp.getValue().setKey(key);
    recordProp.getValue().setState(state);
    recordProp.getValue().setValue(value);
    if (parent != null) {
      parentKeyProp.setValue(parent.getKey());
      parent.childCountProp.increment();
    }
  }

  public ElementInstance(
      final long key, final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {
    this(key, null, state, value);
  }

  public long getKey() {
    return recordProp.getValue().getKey();
  }

  public WorkflowInstanceIntent getState() {
    return recordProp.getValue().getState();
  }

  public void setState(final WorkflowInstanceIntent state) {
    recordProp.getValue().setState(state);
  }

  public WorkflowInstanceRecord getValue() {
    return recordProp.getValue().getValue();
  }

  public void setValue(final WorkflowInstanceRecord value) {
    recordProp.getValue().setValue(value);
  }

  public long getJobKey() {
    return jobKeyProp.getValue();
  }

  public void setJobKey(final long jobKey) {
    jobKeyProp.setValue(jobKey);
  }

  public void decrementChildCount() {
    final int childCount = childCountProp.decrement();

    if (childCount < 0) {
      throw new IllegalStateException(
          String.format("Expected the child count to be positive but was %d", childCount));
    }
  }

  public boolean canTerminate() {
    return WorkflowInstanceLifecycle.canTerminate(getState());
  }

  public boolean isActive() {
    return WorkflowInstanceLifecycle.isActive(getState());
  }

  public boolean isTerminating() {
    return WorkflowInstanceLifecycle.isTerminating(getState());
  }

  public boolean isInFinalState() {
    return WorkflowInstanceLifecycle.isFinalState(getState());
  }

  public void spawnToken() {
    activeTokensProp.increment();
  }

  public void consumeToken() {
    final int activeTokens = activeTokensProp.decrement();

    if (activeTokens < 0) {
      throw new IllegalStateException(
          String.format("Expected the active token count to be positive but was %d", activeTokens));
    }
  }

  public int getNumberOfActiveTokens() {
    return activeTokensProp.getValue();
  }

  public int getNumberOfActiveElementInstances() {
    return childCountProp.getValue();
  }

  public int getNumberOfActiveExecutionPaths() {
    return activeTokensProp.getValue() + childCountProp.getValue();
  }

  public int getMultiInstanceLoopCounter() {
    return multiInstanceLoopCounterProp.getValue();
  }

  public void setMultiInstanceLoopCounter(final int loopCounter) {
    multiInstanceLoopCounterProp.setValue(loopCounter);
  }

  public void incrementMultiInstanceLoopCounter() {
    multiInstanceLoopCounterProp.increment();
  }

  public long getCalledChildInstanceKey() {
    return calledChildInstanceKeyProp.getValue();
  }

  public void setCalledChildInstanceKey(final long calledChildInstanceKey) {
    calledChildInstanceKeyProp.setValue(calledChildInstanceKey);
  }

  public long getInterruptingEventKey() {
    return interruptingEventKeyProp.getValue();
  }

  public void setInterruptingEventKey(final long key) {
    interruptingEventKeyProp.setValue(key);
  }

  public boolean isInterrupted() {
    return getInterruptingEventKey() > 0;
  }

  public long getParentKey() {
    return parentKeyProp.getValue();
  }
}
