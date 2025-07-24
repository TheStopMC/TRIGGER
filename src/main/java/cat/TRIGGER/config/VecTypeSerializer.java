package cat.TRIGGER.config;

import com.google.common.reflect.TypeToken;
import net.minestom.server.coordinate.Vec;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.serialize.ScalarSerializer;

import java.util.function.Predicate;

public class VecTypeSerializer extends ScalarSerializer<Vec> {


    public VecTypeSerializer() {
        super(Vec.class);
    }

    @Override
    public Vec deserialize(TypeToken<?> type, Object obj) throws ObjectMappingException {
        if (obj instanceof String str) {
            String[] parts = str.split(",");
            if (parts.length != 3) {
                throw new ObjectMappingException("Expected Vec format 'x,y,z' but got: " + str);
            }

            try {
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());
                return new Vec(x, y, z);
            } catch (NumberFormatException e) {
                throw new ObjectMappingException("Invalid Vec component in: " + str, e);
            }
        }

        throw new ObjectMappingException("Expected string for Vec3i, got: " + obj.getClass().getName());
    }

    @Override
    public Object serialize(Vec item, Predicate<Class<?>> typeSupported) {
        if (item == null) return null;
        return item.x() + "," + item.y() + "," + item.z();
    }
}

