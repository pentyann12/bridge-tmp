package com.generated.exception;

/** IDLのexception Errors.AccessDeniedException。 */
public class AccessDeniedException extends CorbaUserException {
  private final com.generated.dto.AccessDeniedExceptionDto detail;

  public AccessDeniedException(com.generated.dto.AccessDeniedExceptionDto detail, Throwable cause) {
    super("Errors.AccessDeniedException", "IDL:toru-otsuki.example/Errors/AccessDeniedException:1.0", cause);
    this.detail = detail;
  }

  /** 例外のフィールド。 */
  public com.generated.dto.AccessDeniedExceptionDto detail() {
    return detail;
  }
}
