package petterim1.pterodactylserverupdatetool;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.util.EntityUtils;


import java.io.*;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@SuppressWarnings("CallToPrintStackTrace")
public class App {

    private static final Gson GSON = new Gson();
    private static final SimpleDateFormat SERVER_JAR_DATE_PREFIX_FORMAT = new SimpleDateFormat("yyMMdd-HHmmss-");
    private static final SimpleDateFormat PLUGIN_JAR_DATE_SUFFIX_FORMAT = new SimpleDateFormat("-yyMMdd-HHmmss");
    private static final Type MAP_TYPE_TOKEN = new TypeToken<Map<String, Object>>(){}.getType();

    public static void main(String[] args) {
        System.out.println("Java server update tool for Pterodactyl Panel");
        System.out.println("Made by PetteriM1");
        System.out.println("---------------------");

        Scanner scanner = new Scanner(System.in);
        String host = null;
        String token = null;
        List<String> files = new ArrayList<>();

        System.out.println("Loading host from config...");
        try {
            BufferedReader in = new BufferedReader(new FileReader(System.getProperty("user.dir") + File.separatorChar + "host.txt"));
            host = in.readLine();
            in.close();
            System.out.println("Target host: " + host);
        } catch (Exception e) {
        }

        boolean needsSaving = false;
        if (host == null || host.isEmpty()) {
            System.out.println("No saved host found");
            needsSaving = true;
        }

        while (host == null || host.isEmpty()) {
            System.out.println("Please input the host address");
            host = scanner.nextLine();
        }

        while (host == null || !host.toLowerCase(Locale.ROOT).startsWith("http")) {
            System.out.println("Please input a valid http address");
            host = scanner.nextLine();
        }

        if (host.charAt(host.length() - 1) == '/') {
            host = host.substring(0, host.length() - 1);
        }

        if (needsSaving) {
            System.out.println("Saving the host address...");
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter(System.getProperty("user.dir") + File.separatorChar + "host.txt"));
                out.write(host);
                out.close();
            } catch (Exception e) {
                System.out.println("Failed to save the host address");
                e.printStackTrace();
            }
        }

        System.out.println("Loading servers...");
        try {
            new File(System.getProperty("user.dir") + File.separatorChar + "servers.txt").createNewFile();
        } catch (Exception ignore) {
        }

        List<String> servers = new ArrayList<>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(System.getProperty("user.dir") + File.separatorChar + "servers.txt"));
            String input;
            while (((input = in.readLine()) != null)) {
                String str = input.trim();
                if (!str.isEmpty() && !str.startsWith("#")) {
                    if (servers.contains(input)) {
                        System.out.println("Warning: Duplicated server entry: " + input);
                    } else {
                        servers.add(input);
                    }
                }
            }
            in.close();
        } catch (Exception e) {
        }

        if (servers.isEmpty()) {
            System.out.println("The list of servers is empty. Please list the server IDs in servers.txt");
            return;
        }

        System.out.println("Loaded " + servers.size() + " servers: " + servers);
        System.out.println("Loading saved API token...");
        try {
            BufferedReader in = new BufferedReader(new FileReader(System.getProperty("user.dir") + File.separatorChar + "token.txt"));
            token = in.readLine();
            in.close();
        } catch (Exception e) {
        }

        needsSaving = false;
        if (token == null || token.isEmpty()) {
            System.out.println("No saved API token found");
            needsSaving = true;
        }

        while (token == null || token.isEmpty()) {
            System.out.println("Please input your API token");
            token = scanner.nextLine();
        }

        if (needsSaving) {
            System.out.println("Saving the API token...");
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter(System.getProperty("user.dir") + File.separatorChar + "token.txt"));
                out.write(token);
                out.close();
            } catch (Exception e) {
                System.out.println("Failed to save the API token");
                e.printStackTrace();
            }
        }

        String path = null;
        String pathLowerCase = null;
        while (path == null || !pathLowerCase.endsWith(".jar")) {
            System.out.println("Please input the path of the new server JAR file. Use -delete to delete a file and ; to list next file.");
            path = scanner.nextLine();
            if (path != null) {
                pathLowerCase = path.toLowerCase(Locale.ROOT);
                if (pathLowerCase.endsWith(".jar") || pathLowerCase.endsWith(".jar;")) {
                    String file = path.replace(".jar;", ".jar").replaceAll("\"(.+)\"", "$1");
                    if (files.contains(file)) {
                        System.out.println("Warning: Duplicated upload entry: " + file);
                    } else {
                        files.add(file);
                    }
                }
            }
        }

        System.out.println("Do you want to run following operations on the servers now? (y/N)");
        System.out.println(files);
        boolean confirmed = false;
        while (!confirmed) {
            String input = scanner.nextLine();
            if ("n".equalsIgnoreCase(input) || "no".equalsIgnoreCase(input)) {
                System.out.println("Aborting operations");
                return;
            } else if ("y".equalsIgnoreCase(input) || "yes".equalsIgnoreCase(input)) {
                confirmed = true;
            }
        }

        Date date = new Date();
        String datePrefix = SERVER_JAR_DATE_PREFIX_FORMAT.format(date);
        String dateSuffix = PLUGIN_JAR_DATE_SUFFIX_FORMAT.format(date);

        int operation = 0;
        for (String file : files) {
            boolean delete = file.startsWith("-delete");
            boolean plugin = file.startsWith("-plugin");

            if (delete) {
                file = file.replace("-delete", "");
            }

            if (plugin) {
                file = file.replace("-plugin", "");
            }

            File uploadJar = new File(file);
            String jarFullName = uploadJar.getName();
            HttpEntity data = null;

            if (!delete) {
                if (plugin) {
                    jarFullName = jarFullName.replace(".jar", "") + dateSuffix + ".jar";
                } else {
                    jarFullName = datePrefix + jarFullName;
                }
                data = MultipartEntityBuilder.create().addPart("files", new FileBody(uploadJar, ContentType.DEFAULT_BINARY, jarFullName)).build();
            }

            int success = 0;
            int total = 0;
            long start = System.currentTimeMillis();
            System.out.println("Starting" + (delete ? " deletion " : " uploads ") + (plugin ? "of plugins/" : "of ") + jarFullName + "...");

            for (String server : servers) {
                total++;
                System.out.println("Server " + total + " of " + servers.size());
                try {
                    if (delete) {
                        if (file.startsWith("*") && file.length() > 1) {
                            String[] cmdParts = file.substring(1).split(" ", 2);
                            if (cmdParts.length != 2) {
                                throw new IllegalArgumentException("Not in format '-delete*<n hours or older> <name>.jar'\nExpected 2, found " + cmdParts.length);
                            }

                            int nHours = Integer.parseInt(cmdParts[0]);
                            if (nHours < 0) {
                                throw new IllegalArgumentException("<n hours or older> can't be negative!");
                            }

                            String requestUrl = host + "/api/client/servers/" + server + "/files/list";
                            System.out.println("Connecting to " + requestUrl);
                            HttpClient client = HttpClientBuilder.create().build();
                            HttpGet requestGet = new HttpGet(requestUrl);
                            requestGet.setHeader("Accept", "application/json");
                            requestGet.setHeader("Content-Type", "application/json");
                            requestGet.setHeader("Authorization", "Bearer " + token);
                            HttpResponse response1 = client.execute(requestGet);

                            if (response1.getStatusLine().getStatusCode() != 200) {
                                showError(response1);
                                continue;
                            }

                            Map<String, Object> responses = GSON.fromJson(EntityUtils.toString(response1.getEntity()), MAP_TYPE_TOKEN);
                            List<Map<String, Object>> filesList = (List<Map<String, Object>>) responses.get("data");
                            for (Map<String, Object> fileInfo : filesList) {
                                Map<String, Object> attributes = (Map<String, Object>) fileInfo.get("attributes");
                                if (attributes != null) {
                                    Boolean isFile = (Boolean) attributes.get("is_file");
                                    if (isFile != null && isFile) {
                                        String modifiedAt = (String) attributes.get("modified_at");
                                        if (modifiedAt != null) {
                                            OffsetDateTime createdTime = OffsetDateTime.parse(modifiedAt);
                                            OffsetDateTime currentTime = OffsetDateTime.now();
                                            long createdHoursAgo = Duration.between(createdTime, currentTime).toHours();

                                            if (createdHoursAgo >= nHours || (createdHoursAgo == 0 && nHours == 0)) {
                                                String name = (String) attributes.get("name");
                                                if (name != null && name.endsWith(cmdParts[1])) {
                                                    System.out.println("Found " + name);
                                                    deletePart(server, host, token, name);
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            success++;
                        } else if (deletePart(server, host, token, file)) {
                            success++;
                        }
                    } else {
                        if (plugin) {
                            boolean pluginFound = false;

                            String requestUrl = host + "/api/client/servers/" + server + "/files/list?directory=%2Fplugins";
                            System.out.println("Connecting to " + requestUrl);
                            HttpClient client = HttpClientBuilder.create().build();
                            HttpGet requestGet = new HttpGet(requestUrl);
                            requestGet.setHeader("Accept", "application/json");
                            requestGet.setHeader("Content-Type", "application/json");
                            requestGet.setHeader("Authorization", "Bearer " + token);
                            HttpResponse response1 = client.execute(requestGet);

                            if (response1.getStatusLine().getStatusCode() != 200) {
                                showError(response1);
                                continue;
                            }

                            String pluginNameLowerCase = uploadJar.getName().split("-")[0].toLowerCase(Locale.ROOT);

                            Map<String, Object> responses = GSON.fromJson(EntityUtils.toString(response1.getEntity()), MAP_TYPE_TOKEN);
                            List<Map<String, Object>> filesList = (List<Map<String, Object>>) responses.get("data");
                            for (Map<String, Object> fileInfo : filesList) {
                                Map<String, Object> attributes = (Map<String, Object>) fileInfo.get("attributes");
                                if (attributes != null) {
                                    Boolean isFile = (Boolean) attributes.get("is_file");
                                    if (isFile != null && isFile) {
                                        String name = (String) attributes.get("name");
                                        if (name != null && !jarFullName.equalsIgnoreCase(name)) {
                                            String fileNameLowerCase = name.toLowerCase(Locale.ROOT);
                                            if (fileNameLowerCase.startsWith(pluginNameLowerCase) && fileNameLowerCase.endsWith(".jar")) {
                                                System.out.println(server + " has " + name);
                                                pluginFound = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }

                            if (!pluginFound) {
                                System.out.println(server + " doesn't have the plugin: " + pluginNameLowerCase);
                                continue;
                            }
                        }

                        String requestUrl = host + "/api/client/servers/" + server + "/files/upload";
                        System.out.println("Connecting to " + requestUrl);
                        HttpClient client = HttpClientBuilder.create().build();
                        HttpGet requestGet = new HttpGet(requestUrl);
                        requestGet.setHeader("Accept", "application/json");
                        requestGet.setHeader("Content-Type", "application/json");
                        requestGet.setHeader("Authorization", "Bearer " + token);
                        HttpResponse response1 = client.execute(requestGet);

                        if (response1.getStatusLine().getStatusCode() != 200) {
                            showError(response1);
                            continue;
                        }

                        Map<String, Object> responses = GSON.fromJson(EntityUtils.toString(response1.getEntity()), MAP_TYPE_TOKEN);
                        String uploadUrl = (String) ((Map<?, ?>) responses.get("attributes")).get("url");
                        if (plugin) {
                            uploadUrl += "&directory=%2Fplugins";
                        }
                        System.out.println("Uploading to " + uploadUrl);
                        HttpPost requestPost = new HttpPost(uploadUrl);
                        requestPost.setHeader("Authorization", "Bearer " + token);
                        requestPost.setEntity(data);
                        HttpResponse response2 = client.execute(requestPost);

                        if (response2.getStatusLine().getStatusCode() == 200) {
                            System.out.println("File uploaded to " + server);

                            if (plugin) {
                                System.out.println("Renaming old plugin versions for " + server + "...");
                                requestUrl = host + "/api/client/servers/" + server + "/files/list?directory=%2Fplugins";
                                System.out.println("Connecting to " + requestUrl);
                                client = HttpClientBuilder.create().build();
                                requestGet = new HttpGet(requestUrl);
                                requestGet.setHeader("Accept", "application/json");
                                requestGet.setHeader("Content-Type", "application/json");
                                requestGet.setHeader("Authorization", "Bearer " + token);
                                response1 = client.execute(requestGet);

                                if (response1.getStatusLine().getStatusCode() != 200) {
                                    showError(response1);
                                    continue;
                                }

                                String pluginNameLowerCase = uploadJar.getName().split("-")[0].toLowerCase(Locale.ROOT);

                                responses = GSON.fromJson(EntityUtils.toString(response1.getEntity()), MAP_TYPE_TOKEN);
                                List<Map<String, Object>> filesList = (List<Map<String, Object>>) responses.get("data");
                                for (Map<String, Object> fileInfo : filesList) {
                                    Map<String, Object> attributes = (Map<String, Object>) fileInfo.get("attributes");
                                    if (attributes != null) {
                                        Boolean isFile = (Boolean) attributes.get("is_file");
                                        if (isFile != null && isFile) {
                                            String name = (String) attributes.get("name");
                                            if (name != null && !jarFullName.equalsIgnoreCase(name)) {
                                                String fileNameLowerCase = name.toLowerCase(Locale.ROOT);
                                                if (fileNameLowerCase.startsWith(pluginNameLowerCase) && fileNameLowerCase.endsWith(".jar")) {
                                                    System.out.println("Found " + name);
                                                    renamePart(server, host, token, name);
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                System.out.println("Updating startup settings for " + server + "...");
                                requestUrl = host + "/api/client/servers/" + server + "/startup/variable";
                                System.out.println("Connecting to " + requestUrl);
                                HttpPut requestPut = new HttpPut(requestUrl);
                                requestPut.setHeader("Accept", "application/json");
                                requestPut.setHeader("Content-Type", "application/json");
                                requestPut.setHeader("Authorization", "Bearer " + token);
                                String json = "{" +
                                                "\"key\":\"SERVER_JARFILE\"," +
                                                "\"value\":\"" + jarFullName + "\"" +
                                            "}";
                                requestPut.setEntity(new StringEntity(json));
                                HttpResponse response3 = client.execute(requestPut);

                                if (response3.getStatusLine().getStatusCode() != 200) {
                                    showError(response3);
                                    continue;
                                }
                            }

                            System.out.println("Done! " + server + " is now up to date");
                            success++;
                        } else {
                            showError(response2);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            operation++;
            long time = (System.currentTimeMillis() - start) / 1000;
            System.out.println("Operation " + operation + "/" + files.size() + " done! " + success + " server" + ((success != 1) ? "s " : ' ') + "updated successfully in " + time + " second" + ((time != 1) ? 's' : ' '));
        }

        System.out.println("All operations done!");
    }

    private static void showError(HttpResponse response) {
        try {
            System.out.println("Failed: " + EntityUtils.toString(response.getEntity()));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        System.out.println(response);
    }

    private static boolean deletePart(String server, String host, String token, String name) {
        try {
            String requestUrl = host + "/api/client/servers/" + server + "/files/delete";
            System.out.println("Connecting to " + requestUrl);
            HttpClient client = HttpClientBuilder.create().build();
            HttpPost request = new HttpPost(requestUrl);
            request.setHeader("Accept", "application/json");
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Authorization", "Bearer " + token);
            String json = "{" +
                                "\"root\":\"/\"," +
                                "\"files\":[\"" + name + "\"]" +
                            "}";
            request.setEntity(new StringEntity(json));
            HttpResponse response = client.execute(request);

            if (response.getStatusLine().getStatusCode() == 204) {
                System.out.println("File deleted successfully on " + server);
                return true;
            } else {
                showError(response);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private static boolean renamePart(String server, String host, String token, String name) {
        try {
            String requestUrl = host + "/api/client/servers/" + server + "/files/rename";
            System.out.println("Connecting to " + requestUrl);
            HttpClient client = HttpClientBuilder.create().build();
            HttpPut request = new HttpPut(requestUrl);
            request.setHeader("Accept", "application/json");
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Authorization", "Bearer " + token);
            String json = "{" +
                            "\"root\":\"/plugins\"," +
                            "\"files\":[{" +
                                "\"from\":\"" + name + "\"," +
                                "\"to\":\"" + name + "OLD\"" +
                            "}]" +
                        "}";
            request.setEntity(new StringEntity(json));
            HttpResponse response = client.execute(request);

            if (response.getStatusLine().getStatusCode() == 204) {
                System.out.println("File renamed successfully on " + server);
                return true;
            } else {
                showError(response);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }
}
