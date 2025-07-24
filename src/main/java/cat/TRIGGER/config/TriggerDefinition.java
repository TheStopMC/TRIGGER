package cat.TRIGGER.config;

import net.minestom.server.coordinate.Vec;
import ninja.leaping.configurate.objectmapping.Setting;

import java.util.List;

public class TriggerDefinition {

    @Setting("anchors")
    private List<Vec> anchors;

    @Setting("pos")
    private Vec pos;

    @Setting("debugColor")
    private String debugColor;

    @Setting("imports")
    private String imports;

    @Setting("code")
    private String code;

    public TriggerDefinition() {}

    public List<Vec> getAnchors() { return anchors; }
    public void setAnchors(List<Vec> anchors) { this.anchors = anchors; }

    public Vec getPos() { return pos; }
    public void setPos(Vec pos) { this.pos = pos; }

    public String getDebugColor() { return debugColor; }
    public void setDebugColor(String debugColor) { this.debugColor = debugColor; }

    public String getImports() { return imports; }
    public void setImports(String imports) { this.imports = imports; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}