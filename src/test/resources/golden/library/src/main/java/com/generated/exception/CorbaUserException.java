package com.generated.exception;

/** IDLのraisesで宣言されたユーザー例外の基底クラス。 */
public abstract class CorbaUserException extends RuntimeException {
  private final String repositoryId;

  protected CorbaUserException(String idlName, String repositoryId, Throwable cause) {
    super(idlName, cause);
    this.repositoryId = repositoryId;
  }

  /** IDLのリポジトリID（例: "IDL:example/Errors/NotFound:1.0"）。 */
  public String repositoryId() {
    return repositoryId;
  }
}
