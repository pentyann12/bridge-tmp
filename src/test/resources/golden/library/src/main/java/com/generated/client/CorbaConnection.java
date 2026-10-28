package com.generated.client;

import java.util.Properties;
import java.util.function.Function;
import org.omg.CORBA.ORB;

/**
 * CORBAのORBを1つ持ち、IORからサービスを解決する。
 *
 * <p>プロセスで1つ作り、各サービスのクライアントで共有する。Spring Bootでは自動設定がBeanとして作り、終了時に閉じる。
 */
public final class CorbaConnection implements AutoCloseable {
  /** glassfish-corbaのORBは、このシステムプロパティが無いと初期化に失敗する。 */
  private static final String GMBAL_PROPERTY = "org.glassfish.gmbal.no.multipleUpperBoundsException";

  private final ORB orb;

  public CorbaConnection() {
    this(new Properties());
  }

  /** ORBの設定（org.omg.CORBA.ORBInitialHostなど）を指定して作る。 */
  public CorbaConnection(Properties orbProperties) {
    if (System.getProperty(GMBAL_PROPERTY) == null) {
      System.setProperty(GMBAL_PROPERTY, "true");
    }
    this.orb = ORB.init(new String[0], orbProperties);
  }

  public ORB orb() {
    return orb;
  }

  /** IORの文字列からサービスのオブジェクト参照を作る。narrowには生成されたHelperのnarrowを渡す。 */
  public <T> T resolve(String ior, Function<org.omg.CORBA.Object, T> narrow) {
    return narrow.apply(orb.string_to_object(ior));
  }

  @Override
  public void close() {
    orb.shutdown(false);
    orb.destroy();
  }
}
