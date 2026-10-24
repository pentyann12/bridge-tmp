package com.toru_otsuki.bridge_generator;

import com.toru_otsuki.bridge_generator.gen.GenSettings;
import com.toru_otsuki.bridge_generator.gen.SpringBootProjectGenerator;
import com.toru_otsuki.bridge_generator.idl.IdlScanner;
import com.toru_otsuki.bridge_generator.idl.IdljRunner;
import com.toru_otsuki.bridge_generator.stub.StubIndex;
import com.toru_otsuki.bridge_generator.stub.StubModel;
import com.toru_otsuki.bridge_generator.stub.StubRelocator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// IDLファイルからSpring Bootプロジェクトを生成するまでの全工程を実行する
///
/// 工程は次の順に進む。
///
/// 1. IDLの静的解析（`#pragma prefix`と`-pkgPrefix`の対象名）
/// 1. idljによるスタブ生成
/// 1. デフォルトパッケージに出力されたスタブの再配置
/// 1. スタブのモデル化（サービスと構造体）
/// 1. Spring Bootプロジェクトの生成
///
/// @param options 解析済みのコマンドライン引数
record BridgeGenerator(CliOptions options) {
  /// idljのjarファイル（カレントディレクトリからの相対パス）
  private static final Path IDLJ_PATH = Path.of("tools", "idlj.jar");

  /// 生成を実行する
  ///
  /// @return 生成したSpring Bootプロジェクトのディレクトリ
  /// @throws IOException ファイルの読み書きに失敗した場合
  /// @throws InterruptedException idljの終了待ちで割り込まれた場合
  /// @throws GenerationException 入力の不備やidljの失敗で生成を続けられない場合
  List<Path> run() throws IOException, InterruptedException, GenerationException {
    // 1. #pragma prefixは、最初に値が見つかったIDLのものを全体で使う
    List<IdlScanner.IdlInfo> idls = new ArrayList<>();
    for (Path idlFile : options.idlFiles()) {
      if (!Files.isRegularFile(idlFile)) {
        throw new GenerationException("IDLファイルが見つかりません: " + idlFile);
      }
      idls.add(IdlScanner.scan(idlFile));
    }
    String repoIdPrefix =
        idls.stream()
            .map(IdlScanner.IdlInfo::repoIdPrefix)
            .filter(p -> !p.isEmpty())
            .findFirst()
            .orElse("");

    // 2〜3. スタブを生成し、import可能な形に整える
    Path stubRoot = options.stubRoot();
    Files.createDirectories(stubRoot);
    for (IdlScanner.IdlInfo idl : idls) {
      IdljRunner.run(IDLJ_PATH, idl, stubRoot, options.idlPackagePrefix());
    }
    StubRelocator.relocate(stubRoot, options.idlPackagePrefix());

    // 4. 以降の生成はすべてこのモデルを使い、スタブの構文木には触れない
    StubIndex stubs = StubIndex.load(stubRoot);
    List<StubModel.Service> services =
        stubs.services().stream().map(s -> StubModel.service(s, stubs)).toList();
    if (services.isEmpty()) {
      throw new GenerationException("No Operations interfaces found in generated stubs.");
    }
    List<StubModel.Struct> structs = stubs.structs().stream().map(StubModel::struct).toList();

    // 5. 単一プロジェクトにまとめるか、サービスごとに<サービス名>Apiを作る
    GenSettings settings =
        new GenSettings(
            options.basePackage(),
            options.returnAsDto(),
            options.idlPackagePrefix(),
            repoIdPrefix,
            stubRoot,
            structs);
    List<Path> generated = new ArrayList<>();
    if (options.singleProject()) {
      Path output = options.outputRoot().resolve(options.projectName());
      SpringBootProjectGenerator.generateAll(services, output, settings);
      generated.add(output);
    } else {
      for (StubModel.Service service : services) {
        Path output = options.outputRoot().resolve(service.name() + "Api");
        SpringBootProjectGenerator.generate(service, output, settings);
        generated.add(output);
      }
    }
    return generated;
  }
}
