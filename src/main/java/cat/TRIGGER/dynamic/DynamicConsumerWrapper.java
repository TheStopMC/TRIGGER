package cat.TRIGGER.dynamic;

import cat.TRIGGER.TriggeredCallback;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

import java.util.UUID;
import java.util.function.Consumer;

public class DynamicConsumerWrapper implements Consumer<TriggeredCallback> {

    public static Codec<DynamicConsumerWrapper> CODEC = StructCodec.struct(
            "imports", Codec.STRING, DynamicConsumerWrapper::getImports,
            "functionBody", Codec.STRING, DynamicConsumerWrapper::getFunctionBody,
            (s, s2) -> {
                try {
                    return new DynamicConsumerWrapper(s, s2);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
    );

    private final String functionBody;
    private final String imports;
    private final Consumer<TriggeredCallback> delegate;

    public DynamicConsumerWrapper(String imports, String functionBody) throws Exception {
        this.functionBody = functionBody;
        this.imports = imports;
        this.delegate = RuntimeCompiler.compileConsumer("DynamicClass_" + UUID.randomUUID().toString().replaceAll("-", ""), imports, functionBody);
    }

    public String getFunctionBody() {
        return functionBody;
    }

    public String getImports() {
        return imports;
    }

    @Override
    public void accept(TriggeredCallback o) {
        delegate.accept(o);
    }
}
