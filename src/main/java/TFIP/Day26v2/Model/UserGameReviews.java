package TFIP.Day26v2.Model;

import java.time.LocalDateTime;
import java.util.List;


import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

public class UserGameReviews {
    private String rating;
    private List<Review> games;
    private LocalDateTime timestamp;

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public List<Review> getGames() {
        return games;
    }

    public void setGames(List<Review> games) {
        this.games = games;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public JsonObject toJson() {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        List<JsonObjectBuilder> listOfReview = getGames().stream().map(r -> r.toJsonAgg()).toList();

        for(JsonObjectBuilder x: listOfReview){
            arrayBuilder.add(x);
        }

        return Json.createObjectBuilder()
        .add("rating", getRating())
        .add("games", arrayBuilder)
        .add("timestamp", getTimestamp().toString())
        .build();
    }

}
