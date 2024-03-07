import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.liferay.portal.vulcan.util.ObjectMapperUtil;

public class GenerateBatch {
    private final long _sleepDuration = 200L;
    private final String workspace = "MomenWorkspace";

    public void DoWork() {

        TaskResult<String> batchConfigsTemplate = getTemplate("batch-configs-template.txt");

        TaskResult<String> batchDataTemplate = getTemplate("batch-data-template.txt");

        TaskResult<String> batchEntriesConfigsTemplate = getTemplate("batch-entries-configs-template.txt");

        TaskResult<String> batchEntriesDataTemplate = getTemplate("batch-entries-data-template.txt");

        if (!batchConfigsTemplate.ok()
                || !batchDataTemplate.ok()
                || !batchEntriesConfigsTemplate.ok()
                || !batchEntriesDataTemplate.ok())
            return;

        TaskResult<Map<String, SchemaFullInfo>> fullInfoTask = getFullInfo();

        if (!fullInfoTask.ok()) {
            //TODO : handle errors here
            return;
        }

        Map<String, SchemaFullInfo> fullInfo = fullInfoTask.getResult();

        List<Thread> threads = new ArrayList<>();

        fullInfo.forEach((key, value) -> {
            Thread thread = new Thread(new SingleClientExtensionWork(this.workspace, key, fullInfo.get(key),
                     batchConfigsTemplate.getResult(), batchDataTemplate.getResult(), batchEntriesConfigsTemplate.getResult(), batchEntriesDataTemplate.getResult()));
            threads.add(thread);
            thread.start();
        });

        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (Exception ex) {

            }
        });

        int x = 5;
    }


    private TaskResult<String> getTemplate(String name){
        TaskResult<String> result = new TaskResult<>();

        try {
            InputStream stream = getClass().getResourceAsStream("templates/"+name);

            byte[] bytes = new byte[stream.available()];

            DataInputStream dataInputStream = new DataInputStream(stream);
            dataInputStream.readFully(bytes);

            result.setResult(new String(bytes));
        } catch (Exception e) {
            result.setException(e);
        }

        return result;
    }
    private TaskResult<Map<String, String>> getAllEndpoints() {
        TaskResult<Map<String, String>> result = new TaskResult<>();

        Map<String, String> allEndpoints = new HashMap<>();

        TaskResult<String> allEndpointsResult = Utils.request("http://localhost:8080/o/openapi");

        if (!allEndpointsResult.ok()) {
            result.setException(allEndpointsResult.getException());
            return result;
        }

        HashMap<String, String[]> map = ObjectMapperUtil.readValue(Map.class, allEndpointsResult.getResult());

        map.keySet().forEach(key -> {
            Object[] objects = map.get(key);
            String[] urls = Arrays.stream(objects).map(Object::toString).toArray(String[]::new);
            allEndpoints.put(key, urls[0].replace(".yaml", ".json"));
        });

        result.setResult(allEndpoints);
        return result;
    }

    private TaskResult<Map<String, SchemaFullInfo>> getFullInfo() {
        TaskResult<Map<String, SchemaFullInfo>> result = new TaskResult<>();


        TaskResult<Map<String, String>> allEndpoints = getAllEndpoints();

        List<Thread> threads = new ArrayList<>();
        List<EndpointWork> tasks = new ArrayList<>();

        int endpointIndex = 0;
        for (String endpoint : allEndpoints.getResult().values()) {
            long sleepDuration = endpointIndex++ * this._sleepDuration;
            EndpointWork task = new EndpointWork(endpoint, sleepDuration);

            tasks.add(task);

            Thread thread = new Thread(task);
            thread.start();

            threads.add(thread);
        }


        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (Exception ex) {
                result.setException(ex);
            }
        });

        List<EndpointWork> nonEmptyTasks = tasks.stream().filter(t ->
                        t.getEndpointInfo().ok() && !t.getEndpointInfo().getResult().getSchemasToPathsMap().isEmpty())
                .collect(Collectors.toList());

        Map<String, SchemaFullInfo> fullInfo = new HashMap<>();

        for (EndpointWork task : nonEmptyTasks) {
            Map<String, SchemaFullInfo> tmpMap = task.getEndpointInfo().getResult().getSchemasToPathsMap();

            fullInfo.putAll(tmpMap);
        }

        result.setResult(fullInfo);

        return result;
    }
}

class EndpointWork implements Runnable {
    private final Long _sleepDuration;
    private final String _endpoint;
    private final JsonMapper _jsonMapper;
    private final TaskResult<EndpointInfo> _endpointInfo;

    public EndpointWork(String endpoint, Long sleepDuration) {
        this._endpoint = endpoint;
        this._sleepDuration = sleepDuration;
        this._jsonMapper = new JsonMapper();
        this._endpointInfo = new TaskResult<>();
    }

    @Override
    public void run() {
        try {
            Thread.sleep(this._sleepDuration);
            System.out.println("Running thread: " + Thread.currentThread().getId());
            TaskResult<String> result = Utils.request(_endpoint);

            if (!result.ok()) {
                result.setException(result.getException());
                return;
            }

            EndpointInfo info = _jsonMapper.readValue(result.getResult(), EndpointInfo.class);

            info.buildInfo();

            this._endpointInfo.setResult(info);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            this._endpointInfo.setException(ex);
        }
    }

    public TaskResult<EndpointInfo> getEndpointInfo() {
        return _endpointInfo;
    }

}

class SingleClientExtensionWork implements Runnable {

    private final String _batchConfigsTemplate;
    private final String _batchDataTemplate;
    private final String _batchEntriesConfigsTemplate;
    private final String _batchEntriesDataTemplate;

    private final String _workspace;
    private final String _schemaName;
    private final SchemaFullInfo _schemaFullInfo;
    private final JsonMapper _jsonMapper;

    private final String classNameLastToken;
    private final String prefixLowerCaseDashed;
    private final String prefixUpperCaseSpaced;
    private final String classNameLowerCaseDashed;
    private final String classNameUpperCaseSpaced;
    private final String key;

    public SingleClientExtensionWork(String workspace, String schemaName, SchemaFullInfo schemaFullInfo,
                                     String batchConfigsTemplate, String batchDataTemplate, String batchEntriesConfigsTemplate, String batchEntriesDataTemplate) {

        this._batchConfigsTemplate = batchConfigsTemplate;
        this._batchDataTemplate = batchDataTemplate;
        this._batchEntriesConfigsTemplate = batchEntriesConfigsTemplate;
        this._batchEntriesDataTemplate = batchEntriesDataTemplate;

        this._workspace = workspace;
        this._schemaName = schemaName;
        this._schemaFullInfo = schemaFullInfo;
        this._jsonMapper = new JsonMapper();

        this.classNameLastToken = this.getLastTokenFromClassName(schemaFullInfo.getClassName());
        this.prefixLowerCaseDashed = this.convertToLowerCaseWithDashes(workspace);
        this.prefixUpperCaseSpaced = this.convertToUpperCaseWithSpaces(workspace);
        this.classNameLowerCaseDashed = this.convertToLowerCaseWithDashes(this.classNameLastToken);
        this.classNameUpperCaseSpaced = this.convertToUpperCaseWithSpaces(this.classNameLastToken);
        this.key = schemaFullInfo.getClassName().endsWith("ObjectEntry") ? schemaName : "";
    }

    @Override
    public void run() {
        TaskResult<String> apiResponse = Utils.request(this._schemaFullInfo.getPath());

        if (!apiResponse.ok()) {
            //TODO: handle error
            return;
        }

        JsonNode jsonNode;

        try {
            jsonNode = this._jsonMapper.readValue(apiResponse.getResult(), JsonNode.class);
        } catch (Exception ex) {
            //TODO: handle error
            return;
        }

        long totalCount = jsonNode.get("totalCount").asLong();

        if (totalCount > 0) {
            JsonNode items = jsonNode.get("items");

            String directoryName = getDirectoryName();
            String batchFileName = getBatchFileName();

            String configsTemplate;
            String dataTemplate;

            if (this.classNameLastToken.equals("ObjectEntry")){
                configsTemplate = this._batchEntriesConfigsTemplate;
                dataTemplate = this._batchEntriesDataTemplate;
            }else{
                configsTemplate = this._batchConfigsTemplate;
                dataTemplate = this._batchDataTemplate;
            }

            configsTemplate = replaceValuesInTemaplte(configsTemplate);
            dataTemplate = dataTemplate.replace("<items>", items.toString())
                    .replace("<classname>",this.classNameLastToken)
                    .replace("<key>", this.key);

            try{
                File outputDirectory = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath()+"/output/"+directoryName);
                boolean outputCreated = outputDirectory.mkdirs();

                if (outputCreated){
                    File batchDirectory = new File(outputDirectory.getAbsolutePath()+"/batch");

                    boolean batchDirCreated = batchDirectory.mkdirs();

                    if (batchDirCreated){
                        String configsFileName = outputDirectory.getPath()+"/client-extension.yaml";
                        BufferedWriter configsWriter = new BufferedWriter(new FileWriter(configsFileName));

                        configsWriter.write(configsTemplate);
                        configsWriter.close();

                        String entriesFileName = batchDirectory.getPath()+"/"+batchFileName;
                        BufferedWriter dataWriter = new BufferedWriter(new FileWriter(entriesFileName));

                        dataWriter.write(dataTemplate);
                        dataWriter.close();
                    }
                }else{

                }
            }catch (Exception ex){
                int y= 5;
            }

            int x = 5;
        }

    }

    private String replaceValuesInTemaplte(String template){
        return template.replaceAll("<prefix-lowercase-dashed>", this.prefixLowerCaseDashed)
                .replaceAll("<classname-lowercase-dashed>", this.classNameLowerCaseDashed)
                .replaceAll("<prefix-startcase-spaced>", this.prefixUpperCaseSpaced)
                .replaceAll("<classname-startcase-spaced>", this.classNameUpperCaseSpaced)
                .replaceAll("<key>", this.key);
    }

    private String getBatchFileName(){
        return this.convertToLowerCaseWithDashes(this.classNameLastToken) + ".batch-engine-data.json";
    }
    private String getDirectoryName(){
        if (this.classNameLastToken.equals("ObjectEntry")) {
            return this.prefixLowerCaseDashed+"-"+this.key.toLowerCase()+"-batch-"+this.convertToLowerCaseWithDashes(this.classNameLastToken);
        }
        return this.prefixLowerCaseDashed+"-batch-"+this.convertToLowerCaseWithDashes(this.classNameLastToken);
    }

    private String convertToLowerCaseWithDashes(String word) {
        return word.replaceAll("([A-Z])([A-Z][a-z])", "$1-$2")
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase();
    }

    private String convertToUpperCaseWithSpaces(String word) {
        Pattern pattern = Pattern.compile("\\b\\w");
        Matcher matcher = pattern.matcher(word);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, matcher.group().toUpperCase());
        }
        matcher.appendTail(result);

        return result.toString()
                .replaceAll("([A-Z])([A-Z][a-z])", "$1 $2")
                .replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private String getLastTokenFromClassName(String className) {
        String[] tokens = className.split(Pattern.quote("."));
        return tokens[tokens.length - 1];
    }
}