package com.generated.client;

/**
 * CORBAサービス Guarded を呼ぶクライアント。スレッドセーフ。
 *
 * <p>失敗時は、IDLのraisesで宣言された例外ならその例外クラスを、通信の失敗などなら CorbaCallException を投げる。
 */
public final class GuardedClient {
  private final CorbaConnection connection;
  private final String ior;
  private volatile com.corba.Errors.Guarded service;

  public GuardedClient(CorbaConnection connection, String ior) {
    this.connection = java.util.Objects.requireNonNull(connection, "connection");
    this.ior = java.util.Objects.requireNonNull(ior, "ior");
  }

  private com.corba.Errors.Guarded stub() {
    com.corba.Errors.Guarded s = service;
    if (s == null) {
      s = connection.resolve(ior, com.corba.Errors.GuardedHelper::narrow);
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
      throw new com.generated.exception.CorbaCallException("Guarded.fetch", __e);
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
      throw new com.generated.exception.CorbaCallException("Guarded.check", __e);
    }
  }

  /** check の結果（戻り値とout/inout引数）。 */
  public record CheckResult(String token) {}
}
