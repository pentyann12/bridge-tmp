package com.toru_otsuki.bridge_generator;

import com.toru_otsuki.bridge_generator.gen.GenSettings;
import com.toru_otsuki.bridge_generator.gen.LibraryProjectGenerator;
import com.toru_otsuki.bridge_generator.gen.SpringBootProjectGenerator;
import com.toru_otsuki.bridge_generator.gen.Target;
import com.toru_otsuki.bridge_generator.idl.IdljCompiler;
import com.toru_otsuki.bridge_generator.model.IdlModel;
import com.toru_otsuki.bridge_generator.model.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// IDLファイルからプロジェクト（RESTブリッジか共有ライブラリ）を生成するまでの全工程を実行する
///
/// 1. IDLごとに、idljでスタブを生成し、同じ解析結果からモデル（サービスと構造体）を作る
/// 1. モデルからプロジェクトを生成する
///
/// @param options 解析済みのコマンドライン引数
record BridgeGenerator(CliOptions options) {

  /// 生成を実行する
  ///
  /// @return 生成したSpring Bootプロジェクトのディレクトリ
  /// @throws IOException ファイルの読み書きに失敗した場合
  /// @throws GenerationException 入力の不備やIDLの誤りで生成を続けられない場合
  List<Path> run() throws IOException, GenerationException {
    // 1. 互いにincludeし合うIDLを複数渡されても同じ型が重複しないよう、IDLごとのモデルをまとめる
    Path stubRoot = options.stubRoot();
    Files.createDirectories(stubRoot);
    List<IdlModel> models = new ArrayList<>();
    for (Path idlFile : options.idlFiles()) {
      if (!Files.isRegularFile(idlFile)) {
        throw new GenerationException("IDLファイルが見つかりません: " + idlFile);
      }
      models.add(IdljCompiler.compile(idlFile, stubRoot, options.idlPackagePrefix()));
    }
    IdlModel model = IdlModel.merge(models);
    if (model.services().isEmpty()) {
      throw new GenerationException("IDLにinterfaceが1つもありません");
    }

    // 2. 共有ライブラリなら全サービスを1つに、RESTなら単一プロジェクトにまとめるか、サービスごとに<サービス名>Apiを作る
    GenSettings settings =
        new GenSettings(
            options.basePackage(),
            options.returnAsDto(),
            options.stringCharset(),
            stubRoot,
            model.structs());
    List<Path> generated = new ArrayList<>();
    if (options.target() == Target.LIBRARY) {
      Path output = options.outputRoot().resolve(options.projectName());
      LibraryProjectGenerator.generate(model.services(), output, options.projectName(), settings);
      generated.add(output);
    } else if (options.singleProject()) {
      Path output = options.outputRoot().resolve(options.projectName());
      SpringBootProjectGenerator.generateAll(model.services(), output, settings);
      generated.add(output);
    } else {
      for (Service service : model.services()) {
        Path output = options.outputRoot().resolve(service.name() + "Api");
        SpringBootProjectGenerator.generate(service, output, settings);
        generated.add(output);
      }
    }
    return generated;
  }
}
