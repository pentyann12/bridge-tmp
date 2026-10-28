package com.generated.exception;

/** IDLのexception Errors.NotFound。 */
public class NotFoundException extends CorbaUserException {
  private final com.generated.dto.NotFoundDto detail;

  public NotFoundException(com.generated.dto.NotFoundDto detail, Throwable cause) {
    super("Errors.NotFound", "IDL:toru-otsuki.example/Errors/NotFound:1.0", cause);
    this.detail = detail;
  }

  /** 例外のフィールド。 */
  public com.generated.dto.NotFoundDto detail() {
    return detail;
  }
}
