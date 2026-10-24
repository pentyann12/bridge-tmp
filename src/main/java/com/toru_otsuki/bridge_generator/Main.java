package com.toru_otsuki.bridge_generator;

import java.nio.file.Path;

/// IDLファイルからCORBA-RESTブリッジのSpring Bootプロジェクトを生成するCLIの入口
public class Main {
  private Main() {}

  /// コマンドライン引数を解析して生成を実行し、生成したプロジェクトの場所を表示する
  ///
  /// 引数の誤りは使い方を添えて終了コード2、生成の失敗は終了コード1で終了する。
  ///
  /// @param args コマンドライン引数
  public static void main(String[] args) {
    CliOptions options;
    try {
      options = CliOptions.parse(args);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      System.err.print(CliOptions.usage());
      System.exit(2);
      return;
    }
    if (options.help()) {
      System.out.print(CliOptions.usage());
      return;
    }

    try {
      for (Path project : new BridgeGenerator(options).run()) {
        System.out.println("Spring project generated at: " + project);
      }
    } catch (GenerationException e) {
      // 利用者向けに用意したメッセージなので、スタックトレースは出さない
      System.err.println(e.getMessage());
      System.exit(1);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
