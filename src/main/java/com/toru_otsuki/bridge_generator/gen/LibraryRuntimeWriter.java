package com.toru_otsuki.bridge_generator.gen;

import com.toru_otsuki.bridge_generator.model.Struct;
import java.io.IOException;
import java.nio.file.Files;

/// 共有ライブラリの、IDLによらない実行時クラス（CORBA接続・例外の基底クラス）と、IDLの`exception`ごとの例外クラスを生成する
///
/// 利用者からCORBAの型を隠すため、ユーザー例外もシステム例外も、CORBAのクラスではなくライブラリの`RuntimeException`で投げる。
final class LibraryRuntimeWriter {
  private LibraryRuntimeWriter() {}

  /// IDLの`exception`に対応する、ライブラリの例外クラス名を返す
  ///
  /// 名前が`Exception`で終わっていなければ`Exception`を付ける（`NotFound` → `NotFoundException`）。
  ///
  /// @param idlExceptionName IDLの`exception`のJavaのクラス名
  /// @return 例外クラスの単純名
  static String exceptionClassName(String idlExceptionName) {
    return idlExceptionName.endsWith("Exception")
        ? idlExceptionName
        : idlExceptionName + "Exception";
  }

  /// IDLによらない実行時クラスを出力する
  ///
  /// - `CorbaConnection`: ORBを1つ持ち、IORからサービスを解決する。glassfish-corbaのORBが初期化に必要とするシステムプロパティもここで設定する
  /// - `CorbaUserException`: IDLのユーザー例外に対応する例外の基底クラス
  /// - `CorbaCallException`: 通信の失敗などCORBAのシステム例外を包む例外
  ///
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeRuntime(GenContext ctx) throws IOException {
    String base = ctx.basePackage();
    Files.writeString(
        ctx.clientDir().resolve("CorbaConnection.java"),
        """
        package %s.client;

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
        """
            .formatted(base));

    Files.writeString(
        ctx.exceptionDir().resolve("CorbaUserException.java"),
        """
        package %s.exception;

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
        """
            .formatted(base));

    Files.writeString(
        ctx.exceptionDir().resolve("CorbaCallException.java"),
        """
        package %s.exception;

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
        """
            .formatted(base));
  }

  /// IDLの`exception`1つに対応する例外クラスを出力する。中身は`detail()`でDTOとして取り出せる
  ///
  /// @param struct `exception`のモデル
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeUserException(Struct struct, GenContext ctx) throws IOException {
    String className = exceptionClassName(struct.name());
    String dto = ctx.types().dtoType(struct.type()).toString();
    Files.writeString(
        ctx.exceptionDir().resolve(className + ".java"),
        """
        package %1$s.exception;

        /** IDLのexception %2$s。 */
        public class %3$s extends CorbaUserException {
          private final %4$s detail;

          public %3$s(%4$s detail, Throwable cause) {
            super("%2$s", "%5$s", cause);
            this.detail = detail;
          }

          /** 例外のフィールド。 */
          public %4$s detail() {
            return detail;
          }
        }
        """
            .formatted(ctx.basePackage(), struct.idlName(), className, dto, struct.repoId()));
  }
}
