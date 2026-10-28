package com.generated.client;

/**
 * CORBAサービス ItemService を呼ぶクライアント。スレッドセーフ。
 *
 * <p>失敗時は、IDLのraisesで宣言された例外ならその例外クラスを、通信の失敗などなら CorbaCallException を投げる。
 */
public final class ItemServiceClient {
  private final CorbaConnection connection;
  private final String ior;
  private volatile com.corba.Coverage.ItemService service;

  public ItemServiceClient(CorbaConnection connection, String ior) {
    this.connection = java.util.Objects.requireNonNull(connection, "connection");
    this.ior = java.util.Objects.requireNonNull(ior, "ior");
  }

  private com.corba.Coverage.ItemService stub() {
    com.corba.Coverage.ItemService s = service;
    if (s == null) {
      s = connection.resolve(ior, com.corba.Coverage.ItemServiceHelper::narrow);
      service = s;
    }
    return s;
  }

  public com.generated.dto.ItemDto getItem(int id) {
    try {
      com.corba.Coverage.Item __ret = stub().getItem(id);
      return com.generated.mapper.ItemMapper.toDto(__ret);
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.getItem", __e);
    }
  }

  public void putItem(com.generated.dto.ItemDto item) {
    try {
      stub().putItem(com.generated.mapper.ItemMapper.fromDto(item));
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.putItem", __e);
    }
  }

  public com.generated.dto.TagDto[] listTags(String prefix) {
    try {
      com.corba.Coverage.Tag[] __ret = stub().listTags(com.generated.mapper.StringMapper.decode(prefix));
      return __ret == null ? null : java.util.Arrays.stream(__ret).map(com.generated.mapper.TagMapper::toDto).toArray(com.generated.dto.TagDto[]::new);
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.listTags", __e);
    }
  }

  public String[] echoNames(String[] names) {
    try {
      String[] __ret = stub().echoNames(names == null ? null : java.util.Arrays.stream(names).map(com.generated.mapper.StringMapper::decode).toArray(String[]::new));
      return __ret == null ? null : java.util.Arrays.stream(__ret).map(com.generated.mapper.StringMapper::encode).toArray(String[]::new);
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.echoNames", __e);
    }
  }

  public String greet(String name) {
    try {
      String __ret = stub().greet(com.generated.mapper.StringMapper.decode(name));
      return com.generated.mapper.StringMapper.encode(__ret);
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.greet", __e);
    }
  }

  public com.generated.dto.AnyValue echoAny(com.generated.dto.AnyValue value) {
    try {
      org.omg.CORBA.Any __ret = stub().echoAny(com.generated.mapper.AnyMapper.toAny(value));
      return com.generated.mapper.AnyMapper.toAnyValue(__ret);
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.echoAny", __e);
    }
  }

  public FindItemsResult findItems(String keyword) {
    try {
      com.corba.Coverage.TagSeqHolder __tags = new com.corba.Coverage.TagSeqHolder();
      org.omg.CORBA.StringHolder __message = new org.omg.CORBA.StringHolder();
      int __ret = stub().findItems(com.generated.mapper.StringMapper.decode(keyword), __tags, __message);
      return new FindItemsResult(__ret, __tags.value == null ? null : java.util.Arrays.stream(__tags.value).map(com.generated.mapper.TagMapper::toDto).toArray(com.generated.dto.TagDto[]::new), com.generated.mapper.StringMapper.encode(__message.value));
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.findItems", __e);
    }
  }

  public TouchResult touch(int counter) {
    try {
      org.omg.CORBA.IntHolder __counter = new org.omg.CORBA.IntHolder(counter);
      stub().touch(__counter);
      return new TouchResult(__counter.value);
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.touch", __e);
    }
  }

  public RelabelResult relabel(String label, com.generated.dto.PointDto p) {
    try {
      java.util.Objects.requireNonNull(label, "label");
      org.omg.CORBA.StringHolder __label = new org.omg.CORBA.StringHolder(com.generated.mapper.StringMapper.decode(label));
      java.util.Objects.requireNonNull(p, "p");
      com.corba.Coverage.Inner.PointHolder __p = new com.corba.Coverage.Inner.PointHolder(com.generated.mapper.PointMapper.fromDto(p));
      stub().relabel(__label, __p);
      return new RelabelResult(com.generated.mapper.StringMapper.encode(__label.value), com.generated.mapper.PointMapper.toDto(__p.value));
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.relabel", __e);
    }
  }

  public boolean ping() {
    try {
      boolean __ret = stub().ping();
      return __ret;
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.ping", __e);
    }
  }

  public String[] globalNames(String[] names) {
    try {
      String[] __ret = stub().globalNames(names == null ? null : java.util.Arrays.stream(names).map(com.generated.mapper.StringMapper::decode).toArray(String[]::new));
      return __ret == null ? null : java.util.Arrays.stream(__ret).map(com.generated.mapper.StringMapper::encode).toArray(String[]::new);
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.globalNames", __e);
    }
  }

  public MovePointResult movePoint(com.generated.dto.PointDto p) {
    try {
      com.corba.Coverage.Inner.PointHolder __previous = new com.corba.Coverage.Inner.PointHolder();
      com.corba.Coverage.Inner.Point __ret = stub().movePoint(com.generated.mapper.PointMapper.fromDto(p), __previous);
      return new MovePointResult(com.generated.mapper.PointMapper.toDto(__ret), com.generated.mapper.PointMapper.toDto(__previous.value));
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.movePoint", __e);
    }
  }

  public OutAnyResult outAny() {
    try {
      org.omg.CORBA.AnyHolder __value = new org.omg.CORBA.AnyHolder();
      stub().outAny(__value);
      return new OutAnyResult(com.generated.mapper.AnyMapper.toAnyValue(__value.value));
    } catch (org.omg.CORBA.SystemException __e) {
      throw new com.generated.exception.CorbaCallException("ItemService.outAny", __e);
    }
  }

  /** findItems の結果（戻り値とout/inout引数）。 */
  public record FindItemsResult(int returnValue, com.generated.dto.TagDto[] tags, String message) {}

  /** touch の結果（戻り値とout/inout引数）。 */
  public record TouchResult(int counter) {}

  /** relabel の結果（戻り値とout/inout引数）。 */
  public record RelabelResult(String label, com.generated.dto.PointDto p) {}

  /** movePoint の結果（戻り値とout/inout引数）。 */
  public record MovePointResult(com.generated.dto.PointDto returnValue, com.generated.dto.PointDto previous) {}

  /** outAny の結果（戻り値とout/inout引数）。 */
  public record OutAnyResult(com.generated.dto.AnyValue value) {}
}
