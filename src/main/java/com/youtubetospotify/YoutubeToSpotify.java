package com.youtubetospotify;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;


import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.VideoListResponse;



public class YoutubeToSpotify {
   //copied from YouTube Data API
   private static final String CLIENT_SECRETS= "client_secret.json";
   private static final Collection<String> SCOPES = 
   Arrays.asList("https://www.googleapis.com/auth/youtube.readonly");

   private static final String APPLICATION_NAME = "API code samples";
   private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

   /**
    * Create an authorized Credential object.
    *
    * @return an authorized Credential object.
    * @throws IOException
    */

   //copied from YouTube Data API
   public static Credential authorize(final NetHttpTransport httpTransport) throws IOException {
      //Load client secrets.
      InputStream in = YoutubeToSpotify.class.getResourceAsStream(CLIENT_SECRETS);
      GoogleClientSecrets clientSecrets =
        GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
      //Build flow and trigger user authorization request.
      GoogleAuthorizationCodeFlow flow =
         new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
         .build();
      Credential credential =
          new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
      return credential;
   }

   /**
    * Build and return an authorized API client service.
    *
    * @return an authorized API client service
    * @throws GeneralSecurityException, IOException
    */

   //copied from YouTube Data API
   public static YouTube getService() throws GeneralSecurityException, IOException {
      final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
      Credential credential = authorize(httpTransport);
      return new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
       .setApplicationName(APPLICATION_NAME)
       .build();
   }

   /**
    * Call function to create API service object. Define and
    * execute API request. Print API response.
    *
    * @throws GeneralSecurityException, IOException, GoogleJsonResponseException
    */
   
   static ArrayList<String> get_liked_videos() throws IOException, GeneralSecurityException,
      GoogleJsonResponseException, InterruptedException {
      //copied from YouTube Data API
      YouTube youtubeService = getService();
      //Define and execute the API request
      List<String> part = Arrays.asList("id");  //create List with part parameter

      YouTube.Videos.List request = youtubeService.videos().list(part);

      VideoListResponse response = request.setMaxResults(100L).setMyRating("like").execute();
      //creat json from response
      JSONObject response_json = new JSONObject(response);
      //get items array from respnse
      JSONArray items = response_json.getJSONArray("items");
      //create list for track_uris > Spotify search
      ArrayList<String> track_uris = new ArrayList<String>();
      //loop through items to get id from every liked video
      for (int i = 0; i < items.length(); i++) {
         String song_id = items.getJSONObject(i).getString("id");
         //create process to use yt-dlb  
         Process yt_dl = new ProcessBuilder(
            "", //path to yt-dlp
            "--skip-download",
            "-O%(track)s %(artist)s",
            song_id
         ).start();
         //create reader to get track and artist from song
         BufferedReader reader = new BufferedReader(new InputStreamReader(yt_dl.getInputStream()));   
         String line = reader.readLine();
         String track_artist = line;
         //create array with unallowed keywords for Spotify search
         String[] not_allowed = {"NA", "feat", "Feat", "featuring", "Featuring"};
         //loop through track and artist to remove unallowed keywords
         for (int y = 0; y < not_allowed.length; y++) {
            String test = not_allowed[y];
            if (line.contains(test)) {
               track_artist = line.replaceAll(test, "");
            }
         }
         //only use song if track and artist was found
         if (!track_artist.isBlank()) {
         //remove parentheses if there are any
         track_artist = track_artist.replaceAll("\\(.*\\)", "");
         //call method to get spotify uri for the song and add it to the list
         track_uris.add(get_spotify_uri(track_artist));
         }
      }
      //used in main method
      return track_uris;
   }

   static String create_playlist() throws IOException, InterruptedException {
      //URL for query to the Spotify Web API completed with spotify_user_id
      String query = "https://api.spotify.com/v1/users/%s/playlists";
      query = String.format(query, secret.spotify_user_id);
      //create json with the request body
      JSONObject request_body = new JSONObject();
      request_body.put("name", "YouTube: Gefällt mir");
      request_body.put("description", "Lieder von YouTube");
      request_body.put("public", false);
      //convert request body to string
      String request_string = request_body.toString();
      //create HTTPClient
      var client = HttpClient.newHttpClient();
      //create HTTPRequest with the corresponding parameters
      var request = HttpRequest.newBuilder()
         .uri(URI.create(query))
         .headers("Content-Type", "application/json", "Authorization", secret.spotify_token)
         .POST(BodyPublishers.ofString(request_string))
         .build();
      //get response as string
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      //convert response from string to json
      JSONObject search_result = new JSONObject(response.body());
      //get playlist id as string
      String playlist_id = search_result.getString("id");
      //used in add_song_to_playlist method
      return playlist_id;
   } 

    private static String get_spotify_uri(String track_artist) throws IOException, InterruptedException {
      //remove special characters from track and artist 
      String track_artist_formatted = track_artist.replaceAll("[^a-zA-Z0-9]"," ").replaceAll("\\s","%20");
      //URL for query to the Spotify Web API completed with track and artist
      String query = "https://api.spotify.com/v1/search?q={0}&type=track&limit=1&offset=0";
      String query_formatted = java.text.MessageFormat.format(query, track_artist_formatted);
      //create HTTPClient
      var client = HttpClient.newHttpClient();
      //create HTTPRequest with the corresponding parameters
      var request = HttpRequest.newBuilder()
         .uri(URI.create(query_formatted))
         .headers("Content-Type", "application/json", "Authorization", secret.spotify_token)
         .GET()
         .build();
      //get response as string
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      //convert response from string to json
      JSONObject search_result = new JSONObject(response.body());
      String song_uri = "";
      //get items object from response
      JSONObject tracks = search_result.getJSONObject("tracks");
      //loop through items to get song uri
      JSONArray items = tracks.getJSONArray("items");
      for (int i = 0; i < items.length(); i++) {
         song_uri = items.getJSONObject(i).getString("uri");
      }
      //used in get_liked_videos method
      return song_uri;
   }
     
   static void add_song_to_playlist(ArrayList<String> spotify_uris) throws IOException, InterruptedException {
      //create json with request body, add song uris and convert to string
      JSONObject request_body = new JSONObject();
      request_body.put("uris", spotify_uris);
      String request_string = request_body.toString();
      //call create_playlist method to get playlist id
      String playlist_id = create_playlist();
      //URL for query to the Spotify Web API completed with playlist id
      String query = "https://api.spotify.com/v1/playlists/{0}/tracks";
      String query_formatted = java.text.MessageFormat.format(query, playlist_id);
      //create HTTPClient
      var client = HttpClient.newHttpClient();
      //create HTTPRequest with the corresponding parameters
      var request = HttpRequest.newBuilder()
         .uri(URI.create(query_formatted))
         .headers("Content-Type", "application/json", "Authorization", secret.spotify_token)
         .POST(BodyPublishers.ofString(request_string))
         .build();
      //send request
      client.send(request, BodyHandlers.ofString());
   }

   public static void main(String[] args) throws IOException, InterruptedException, GeneralSecurityException  {
   //call method get_liked_videos within add_song_to_playlist method
   add_song_to_playlist(get_liked_videos());
  }      
 }