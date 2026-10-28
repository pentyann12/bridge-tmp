package com.generated.client;

import java.util.Properties;
import java.util.function.Function;
import org.omg.CORBA.ORB;

/**
 * CORBAのORBを1つ持ち、IORからサービスを解決する。
 *
 * <p>アプリで1つ作り、各サービスのクライアントで共有する。Spring Bootでは自動設定がBeanとして作り、終了時に閉じる。
 *
 * <p>ORBは既定ではJMXにMBeanを登録しない。glassfish-corbaのORBはJVMで共有されるMBeanサーバーに固定の名前で登録するため、
 * このライブラリを使うWebアプリを同じTomcatに複数（パラレルデプロイを含む）置くと、2つ目以降のORBが初期化に失敗するため。
 */
public final class CorbaConnection implements AutoCloseable {
  /** glassfish-corbaのORBは、このシステムプロパティが無いと初期化に失敗する。 */
  private static final String GMBAL_PROPERTY = "org.glassfish.gmbal.no.multipleUpperBoundsException";

  /** ORBの設定のうち、ライブラリが既定値を変えているもの。 */
  private static final String REGISTER_MBEANS_PROPERTY = "com.sun.corba.ee.ORBRegisterMBeans";

  private final ORB orb;

  public CorbaConnection() {
    this(new Properties());
  }

  /** ORBの設定（org.omg.CORBA.ORBInitialHostなど）を指定して作る。指定した設定はライブラリの既定値より優先する。 */
  public CorbaConnection(Properties orbProperties) {
    if (System.getProperty(GMBAL_PROPERTY) == null) {
      System.setProperty(GMBAL_PROPERTY, "true");
    }
    Properties properties = new Properties();
    properties.setProperty(REGISTER_MBEANS_PROPERTY, "false");
    properties.putAll(orbProperties);
    this.orb = ORB.init(new String[0], properties);
    // Anyもこのアプリ専用のORBで作る（シングルトンORBは内部のORBを固定の名前でJMXに登録してしまう）
    com.generated.mapper.AnyMapper.useOrb(orb);
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
    com.generated.mapper.AnyMapper.releaseOrb(orb);
    orb.shutdown(false);
    orb.destroy();
  }
}
