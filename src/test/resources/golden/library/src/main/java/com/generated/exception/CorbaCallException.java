package com.generated.exception;

import org.omg.CORBA.SystemException;

/** 接続できない・通信が切れたなど、CORBAのシステム例外で呼び出しに失敗したことを表す。 */
public class CorbaCallException extends RuntimeException {
  private final String operation;

  public CorbaCallException(String operation, SystemException cause) {
    super(operation + " の呼び出しに失敗しました: " + cause, cause);
    this.operation = operation;
  }

  /** 失敗した操作（例: "ItemService.getItem"）。 */
  public String operation() {
    return operation;
  }

  /** 原因となったCORBAのシステム例外（TRANSIENT・COMM_FAILUREなど）。 */
  public SystemException systemException() {
    return (SystemException) getCause();
  }
}
