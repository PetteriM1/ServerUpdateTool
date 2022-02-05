package me.petterim1.pterodactylserverupdatetool;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.util.EntityUtils;


import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class App {

    public static void main(String[] args) {
        System.out.println("ServerUpdateTool version 1.0");
        System.out.println("Made by PetteriM1");
        System.out.println("---------------------");
        Scanner scanner = new Scanner(System.in);
        String host = null;
        String token = null;
        String file = null;
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
        while (host == null || !host.toLowerCase().startsWith("http")) {
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
                    servers.add(input);
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
        while (file == null || !file.toLowerCase().endsWith(".jar")) {
            System.out.println("Please input the path of the new server JAR file");
            file = scanner.nextLine();
            if (file != null) {
                file = file.replaceAll("\"(.+)\"", "$1");
            }
        }
        long start = System.currentTimeMillis();
        int count = 0;
        File serverJar = new File(file);
        String jarName = serverJar.getName();
        HttpEntity data = MultipartEntityBuilder.create().addPart("files", new FileBody(serverJar)).build();
        System.out.println("Starting uploads of " + jarName + "...");
        for (String server : servers) {
            try {
                String requestUrl = host + "/api/client/servers/" + server + "/files/upload";
                System.out.println("Connecting to " + requestUrl);
                HttpClient client = HttpClientBuilder.create().build();
                HttpGet requestGet = new HttpGet(requestUrl);
                requestGet.setHeader("Accept", "application/json");
                requestGet.setHeader("Content-Type", "application/json");
                requestGet.setHeader("Authorization", "Bearer " + token);
                HttpResponse response1 = client.execute(requestGet);
                if (response1.getStatusLine().getStatusCode() != 200) {
                    System.out.println("Failed: " + EntityUtils.toString(response1.getEntity()));
                    System.out.println(response1);
                    continue;
                }
                Map<String, Object> responses = new Gson().fromJson(EntityUtils.toString(response1.getEntity()), new MapTypeToken().getType());
                String uploadUrl = (String) ((Map<?, ?>) responses.get("attributes")).get("url");
                System.out.println("Uploading to " + uploadUrl);
                HttpPost requestPost = new HttpPost(uploadUrl);
                requestPost.setHeader("Authorization", "Bearer " + token);
                requestPost.setEntity(data);
                HttpResponse response2 = client.execute(requestPost);
                if (response2.getStatusLine().getStatusCode() == 200) {
                    System.out.println("File uploaded to " + server);
                    System.out.println("Updating startup settings for " + server + "...");
                   requestUrl = host + "/api/client/servers/" + server + "/startup/variable";
                    System.out.println("Connecting to " + requestUrl);
                    HttpPut requestPut = new HttpPut(requestUrl);
                    requestPut.setHeader("Accept", "application/json");
                    requestPut.setHeader("Content-Type", "application/json");
                    requestPut.setHeader("Authorization", "Bearer " + token);
                    String json = "{\r\n" +
                            "  \"key\": \"SERVER_JARFILE\",\r\n" +
                            "  \"value\": \"" + jarName + "\"\r\n" +
                            "}";
                    requestPut.setEntity(new StringEntity(json));
                    HttpResponse response3 = client.execute(requestPut);
                    if (response3.getStatusLine().getStatusCode() != 200) {
                        System.out.println("Failed: " + EntityUtils.toString(response3.getEntity()));
                        System.out.println(response3);
                        continue;
                    }
                    System.out.println("Done! " + server + " is now up to date");
                    count++;
                } else {
                    System.out.println("Failed: " + EntityUtils.toString(response2.getEntity()));
                    System.out.println(response2);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        long time = (System.currentTimeMillis() - start) / 1000;
        System.out.println("All operations done! " + count + " server" + ((count != 1) ? "s " : ' ') + "updated in " + time + " second" + ((time != 1) ? 's' : ' '));
    }

    private static class MapTypeToken extends TypeToken<Map<String, Object>> {
    }
}
