import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.liferay.portal.kernel.util.StringUtil;
import org.atteo.evo.inflector.English;

import java.util.*;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EndpointInfo {

    @JsonProperty("paths")
    private Map<String,PathAction> paths;
    @JsonProperty("servers")
    private Map<String,String>[] servers;
    @JsonProperty("components")
    private Components components;

    @JsonIgnore
    private Map<String,SchemaFullInfo> schemasToPathsMap = new HashMap<>();
    public Map<String, PathAction> getPaths() {
        return paths;
    }

    public void setPaths(Map<String, PathAction> paths) {
        this.paths = paths;
    }

    public Map<String, SchemaFullInfo> getSchemasToPathsMap() {
        return schemasToPathsMap;
    }

    public void setSchemasToPathsMap(Map<String, SchemaFullInfo> schemasToPathsMap) {
        this.schemasToPathsMap = schemasToPathsMap;
    }

    public Map<String, String>[] getServers() {
        return servers;
    }

    public void setServers(Map<String, String>[] servers) {
        this.servers = servers;
    }

    public Components getComponents() {
        return components;
    }

    public void setComponents(Components components) {
        this.components = components;
    }

    public void validatePaths(){
        this.paths = this.paths.entrySet().stream()
                .filter(e-> Objects.nonNull(e.getValue().getGet()) &&
                        Objects.nonNull(e.getValue().getGet().getOperationId()))
                .collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
    }

    public void mapSchemasToPaths(){
        try{
            this.components.getSchemas().entrySet().forEach(e -> {
                String suggestedOperationId = ("get"+ English.plural(e.getKey()) +"page").toLowerCase();

                Optional<Map.Entry<String,PathAction>> matchingPathAction =
                        this.paths.entrySet().stream().filter(path -> {
                            String operationId = path.getValue().getGet().getOperationId().toLowerCase();

                            return !path.getKey().contains("{") && StringUtil.equals(suggestedOperationId, operationId);
                        }).findFirst();

                if (matchingPathAction.isPresent()){

                    String server = this.servers[0].get("url");
                    server = server.substring(0,server.length()-1);

                    SchemaFullInfo schemaFullInfo = new SchemaFullInfo();

                    schemaFullInfo.setPath(server+matchingPathAction.get().getKey());
                    schemaFullInfo.setClassName(e.getValue().getClassName());

                    this.schemasToPathsMap.put(e.getKey(),schemaFullInfo);
                }
            });
        }catch (Exception ex){
            int x = 5;
        }
    }

    public void buildInfo(){
        if (this.getComponents() != null){
            this.getComponents().validateSchemas();
            this.validatePaths();
            this.mapSchemasToPaths();
        }
    }
}

class SchemaFullInfo{
    private String path;
    private String className;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }
}

class Components{
    @JsonProperty("schemas")
    private Map<String, SchemaInfo> schemas;

    public Map<String, SchemaInfo> getSchemas() {
        return schemas;
    }

    public void setSchemas(Map<String, SchemaInfo> schemas) {
        this.schemas = schemas;
    }

    public void validateSchemas(){
        this.schemas = schemas.entrySet().stream()
                .filter(entry -> Objects.nonNull(entry.getValue().getClassName()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class SchemaInfo{
    @JsonProperty("properties")
    private Map<String,JsonNode> properties;
    @JsonIgnore
    private String className;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public SchemaInfo(@JsonProperty("properties") Map<String,JsonNode> _properties){
        if (_properties != null){
            this.properties = _properties;
            if (_properties.get("x-class-name") != null){
                className =  _properties.get("x-class-name").get("default").asText();
            }
        }
    }

    public Map<String, JsonNode> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, JsonNode> properties) {
        this.properties = properties;
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class PathAction{
    @JsonProperty("get")
    private PathActionInfo get;

    public PathActionInfo getGet() {
        return get;
    }

    public void setGet(PathActionInfo get) {
        this.get = get;
    }
}
@JsonIgnoreProperties(ignoreUnknown = true)
class PathActionInfo{
    @JsonProperty("operationId")
    private String operationId;

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }
}
