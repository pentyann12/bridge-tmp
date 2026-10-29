package com.generated.client;

/**
 * CORBAサービス Reviewed を呼ぶクライアント。スレッドセーフ。
 *
 * <p>失敗時は、IDLのraisesで宣言された例外ならその例外クラスを、通信の失敗などなら CorbaCallException を投げる。
 */
public final class ReviewedClient {
  private final CorbaConnection connection;
  private final String ior;
  private volatile com.corba.Errors.Reviewed service;

  public ReviewedClient(CorbaConnection connection, String ior) {
    this.connection = java.util.Objects.requireNonNull(connection, "connection");
    this.ior = java.util.Objects.requireNonNull(ior, "ior");
  }

  private com.corba.Errors.Reviewed stub() {
    com.corba.Errors.Reviewed s = service;
    if (s == null) {
      s = connection.resolve(ior, com.corba.Errors.ReviewedHelper::narrow);
      service = s;
    }
    return s;
  }

  public String fetch(int id) {
    try {
      String __ret = stub().fetch(id);
      return com.generated.mapper.StringMapper.encode(__ret);
    } catch (com.corba.Errors.NotFound __e) {
      throw new com.generated.exception.NotFoundException(com.generated.mapper.NotFoundMapper.toDto(__e), __e);
    } catch (com.corba.Errors.AccessDeniedException __e) {
      throw new com.generated.exception.AccessDeniedException(com.generated.mapper.AccessDeniedExceptionMapper.toDto(__e), __e);
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("Reviewed.fetch", __e);
    }
  }

  public CheckResult check(String token) {
    try {
      java.util.Objects.requireNonNull(token, "token");
      org.omg.CORBA.StringHolder __token = new org.omg.CORBA.StringHolder(com.generated.mapper.StringMapper.decode(token));
      stub().check(__token);
      return new CheckResult(com.generated.mapper.StringMapper.encode(__token.value));
    } catch (com.corba.Errors.AccessDeniedException __e) {
      throw new com.generated.exception.AccessDeniedException(com.generated.mapper.AccessDeniedExceptionMapper.toDto(__e), __e);
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("Reviewed.check", __e);
    }
  }

  public boolean approve(int id) {
    try {
      boolean __ret = stub().approve(id);
      return __ret;
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("Reviewed.approve", __e);
    }
  }

  /** check の結果（戻り値とout/inout引数）。 */
  public record CheckResult(String token) {}
}
