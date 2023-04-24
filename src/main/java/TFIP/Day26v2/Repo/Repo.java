package TFIP.Day26v2.Repo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.LimitOperation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation.AddFieldsOperationBuilder;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.util.SystemPropertyUtils;

import TFIP.Day26v2.Exception.GameNotFoundException;
import TFIP.Day26v2.Exception.ReviewNotFoundException;
import TFIP.Day26v2.Model.EditedReview;
import TFIP.Day26v2.Model.Game;
import TFIP.Day26v2.Model.Review;

@Repository
public class Repo {

    @Autowired
    private MongoTemplate template;

    public List<Game> getAllGames(Integer offset, Integer limit) {

        Query query = new Query(
                new Criteria().andOperator(
                        // gt = greater than
                        Criteria.where("gid").gt(offset),
                        // lte = less than and equal to
                        Criteria.where("gid").lte(offset + limit)));

        List<Game> result = template.find(query, Document.class, "games")
                // convert to java steam
                .stream()
                // map each document to game object (convert document to game obj)
                .map(d -> Game.createFromMongo(d))
                // collect game obj into a list
                .toList();

        // Query query = new Query();
        // Pageable pageable = PageRequest.of(offset, limit);
        // query.with(pageable);
        // return template.find(query, Document.class, "games").stream().map(d ->
        // Game.createFromMongo(d)).toList();

        return result;
    }

    public List<Game> getGamesByRank(Integer offset, Integer limit) {

        Query query = new Query();
        Pageable pageable = PageRequest.of(offset, limit);
        query.with(pageable);
        Sort sort = Sort.by(Sort.Direction.ASC, "ranking");
        query.with(sort);

        List<Game> result = template.find(query, Document.class, "games")
                // convert to java steam
                .stream()
                // map each document to game object (convert document to game obj)
                .map(d -> Game.createFromMongo(d))
                // collect game obj into a list
                .toList();

        return result;
    }

    public Game getGameById(String game_id) throws GameNotFoundException {
        Query query = new Query();
        // checks whether game_id is a valid mongoDB objectID
        if (ObjectId.isValid(game_id)) {
            Criteria criteria = Criteria.where("_id").is(new ObjectId(game_id));
            query.addCriteria(criteria);
        } else {
            Criteria criteria = Criteria.where("gid").is(Integer.parseInt(game_id));
            query.addCriteria(criteria);
        }
        // find return a list
        // findOne return just 1
        Game result = template.findOne(query, Game.class, "games");

        if (result == null) {
            throw new GameNotFoundException("Game not found");

        }

        return result;

        // When you use Document.class as the second argument,
        // Spring Data MongoDB will return a Document object that
        // contains the raw BSON document retrieved from MongoDB without
        // deserializing it into a Java object

        // can convert Document to Game class
        // Document document = mongoTemplate.findOne(query, Document.class, "games");
        // Game game = mongoTemplate.getConverter().read(Game.class, document);

    }

    public void newReview(Review review) {
        Document doc = new Document()
                .append("user", review.getUser())
                .append("rating", review.getRating())
                .append("comment", review.getComment())
                .append("ID", review.getID())
                .append("posted", review.getPosted())
                .append("name", review.getName());

        template.insert(doc, "reviews");
    }

    public Review getReviewById(String review_id) throws ReviewNotFoundException {

        Query query = new Query();
        Criteria criteria = Criteria.where("_id").is(review_id);
        query.addCriteria(criteria);
        Review review = template.findOne(query, Review.class, "reviews");

        if (review == null) {
            throw new ReviewNotFoundException("Review not found");
        }

        return review;
    }

    public void updateReview(Review review, String review_id) {
        Criteria criteria = Criteria.where("_id").is(review_id);

        Query query = new Query(criteria);
        Update update = new Update()
                .set("comment", review.getComment())
                .set("rating", review.getRating())
                .set("posted", review.getPosted())
                .set("reviewList", review.getReviewList());

        template.updateFirst(query, update, Review.class, "reviews");
        System.out.println("updated");

    }

    public Optional<Game> aggregateGameReviews(String game_id) {
        // find game_id in games collection
        MatchOperation matchId = Aggregation.match(Criteria.where("_id").is(game_id));
        // establish rs between games and review column
        // localfield = input document
        // foreigfield = reviews doc
        // return an array
        // as is ails for the reviews doc
        LookupOperation lookOp = Aggregation.lookup("reviews", "gid", "gid", "reviews");

        // only reviews._id is include
        // reviews.id is return as an array 
        ProjectionOperation pop = Aggregation
                .project("_id", "gid", "name", "year", "ranking", "users_rated", "url", "image").and("reviews._id")
                .as("reviews");

        AddFieldsOperationBuilder add = Aggregation.addFields();
        add.addFieldWithValue("timestamp", LocalDateTime.now());
        AddFieldsOperation newField = add.build();

        Aggregation pipeline = Aggregation.newAggregation(matchId, lookOp, pop, newField);

        AggregationResults<Document> d = template.aggregate(pipeline, "games", Document.class);

        if (!d.iterator().hasNext())
            return Optional.empty();

        Document doc = d.iterator().next();
        Game g = Game.createFromMongoAgg(doc);
        return Optional.of(g);
    }

    public Optional<List<Document>> aggregateGameReviewsByUser(String username, String ranking) {

        Criteria criteria = Criteria.where("user").is(username);
        MatchOperation match = Aggregation.match(criteria);

        SortOperation sortByRating;
        if (ranking.equals("highest")) {
            sortByRating = Aggregation.sort(Sort.by(Direction.DESC, "rating"));
        } else {
            sortByRating = Aggregation.sort(Sort.by(Direction.ASC, "rating"));
        }
        // local is from input
        // foreign is from games
        LookupOperation lookUp = Aggregation.lookup("games", "gid", "gid", "games");

        ProjectionOperation project = Aggregation.project("_id", "rating", "user").and("games.name").as("name").and("games.year").as("year")
                .and("c_text").as("comment").and("c_id").as("review_id");

        LimitOperation limitResult = Aggregation.limit(10);

        Aggregation pipeline = Aggregation.newAggregation(match, sortByRating, lookUp, limitResult, project);

        AggregationResults<Document> result = template.aggregate(pipeline, "reviews", Document.class);

        List<Document> listOfDoc = new ArrayList<>();
        Iterator<Document> iterator = result.iterator();

        if (!iterator.hasNext()) {
            System.out.println("empty");
            return Optional.empty();
        }

        while (iterator.hasNext()) {
            Document doc = iterator.next();
            listOfDoc.add(doc);
            System.out.println("Not empty");
        }

        return Optional.of(listOfDoc);
    }

}