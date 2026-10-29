package com.toru_otsuki.bridge_generator.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// IDLから取り出した、コード生成に必要な情報
///
/// @param services サービス（IDLの`interface`）
/// @param structs DTOとMapperを生成する型
/// @param enums DTO（Javaのenum）とMapperを生成するenum
public record IdlModel(List<Service> services, List<Struct> structs, List<IdlEnum> enums) {

  /// 複数のIDLファイルから得たモデルを1つにまとめる
  ///
  /// 同じIDLを互いにincludeしていると同じ型が何度も現れるので、Javaの完全修飾名で1つにする。
  /// 並び順はJavaのファイルパスの順（大文字小文字を区別しない）にそろえ、IDLファイルの渡し方によらず生成物が同じになるようにする。
  ///
  /// @param models IDLファイルごとのモデル
  /// @return まとめたモデル
  public static IdlModel merge(Collection<IdlModel> models) {
    Map<String, Service> services = new LinkedHashMap<>();
    Map<String, Struct> structs = new LinkedHashMap<>();
    Map<String, IdlEnum> enums = new LinkedHashMap<>();
    for (IdlModel m : models) {
      m.services().forEach(s -> services.putIfAbsent(path(s.pkg(), s.name() + "Operations"), s));
      m.structs().forEach(s -> structs.putIfAbsent(path(s.pkg(), s.name()), s));
      m.enums().forEach(e -> enums.putIfAbsent(path(e.pkg(), e.name()), e));
    }
    return new IdlModel(sorted(services), sorted(structs), sorted(enums));
  }

  private static String path(String pkg, String name) {
    return (pkg.isEmpty() ? name : pkg.replace('.', '/') + "/" + name).toUpperCase(Locale.ROOT);
  }

  private static <T> List<T> sorted(Map<String, T> byPath) {
    return byPath.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
        .map(Map.Entry::getValue)
        .toList();
  }
}
