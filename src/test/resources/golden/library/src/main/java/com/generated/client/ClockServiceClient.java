package com.generated.client;

/**
 * CORBAサービス ClockService を呼ぶクライアント。スレッドセーフ。
 *
 * <p>失敗時は、IDLのraisesで宣言された例外ならその例外クラスを、通信の失敗などなら CorbaCallException を投げる。
 */
public final class ClockServiceClient {
  private final CorbaConnection connection;
  private final String ior;
  private volatile com.corba.Other.ClockService service;

  public ClockServiceClient(CorbaConnection connection, String ior) {
    this.connection = java.util.Objects.requireNonNull(connection, "connection");
    this.ior = java.util.Objects.requireNonNull(ior, "ior");
  }

  private com.corba.Other.ClockService stub() {
    com.corba.Other.ClockService s = service;
    if (s == null) {
      s = connection.resolve(ior, com.corba.Other.ClockServiceHelper::narrow);
      service = s;
    }
    return s;
  }

  public long now() {
    try {
      long __ret = stub().now();
      return __ret;
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ClockService.now", __e);
    }
  }

  public String zone(com.generated.dto.TagDto hint) {
    try {
      String __ret = stub().zone(com.generated.mapper.TagMapper.fromDto(hint));
      return com.generated.mapper.StringMapper.encode(__ret);
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ClockService.zone", __e);
    }
  }
}
