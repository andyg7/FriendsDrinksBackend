package andrewgrant.friendsdrinks.frontend.restapi.friendsdrinks;

import static andrewgrant.friendsdrinks.frontend.restapi.StreamsService.*;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import andrewgrant.friendsdrinks.api.avro.*;
import andrewgrant.friendsdrinks.avro.FriendsDrinksState;
import andrewgrant.friendsdrinks.frontend.restapi.friendsdrinks.post.PostFriendsDrinksRequestBean;
import andrewgrant.friendsdrinks.frontend.restapi.friendsdrinks.post.PostFriendsDrinksResponseBean;
import andrewgrant.friendsdrinks.user.avro.UserEvent;
import andrewgrant.friendsdrinks.user.avro.UserId;
import andrewgrant.friendsdrinks.user.avro.UserSignedUp;

/**
 * Implements frontend REST API friendsdrinks path.
 */
@Path("")
public class Handler {

    private KafkaStreams kafkaStreams;
    private KafkaProducer<String, FriendsDrinksEvent> friendsDrinksKafkaProducer;
    private KafkaProducer<UserId, UserEvent> userKafkaProducer;
    private Properties envProps;

    public Handler(KafkaStreams kafkaStreams,
                   KafkaProducer<String, FriendsDrinksEvent> friendsDrinksKafkaProducer,
                   KafkaProducer<UserId, UserEvent> userKafkaProducer,
                   Properties envProps) {
        this.kafkaStreams = kafkaStreams;
        this.friendsDrinksKafkaProducer = friendsDrinksKafkaProducer;
        this.userKafkaProducer = userKafkaProducer;
        this.envProps = envProps;
    }

    @GET
    @Path("/friendsdrinks")
    @Produces(MediaType.APPLICATION_JSON)
    public GetAllFriendsDrinksResponseBean getAllFriendsDrinks() {
        ReadOnlyKeyValueStore<FriendsDrinksId, andrewgrant.friendsdrinks.avro.FriendsDrinksState> kv =
                kafkaStreams.store(StoreQueryParameters.fromNameAndType(FRIENDSDRINKS_STORE, QueryableStoreTypes.keyValueStore()));
        KeyValueIterator<FriendsDrinksId, andrewgrant.friendsdrinks.avro.FriendsDrinksState> allKvs = kv.all();
        List<FriendsDrinksBean> friendsDrinksList = new ArrayList<>();
        while (allKvs.hasNext()) {
            KeyValue<FriendsDrinksId, andrewgrant.friendsdrinks.avro.FriendsDrinksState> keyValue = allKvs.next();
            FriendsDrinksState friendsDrinksState = keyValue.value;
            FriendsDrinksBean friendsDrinksBean = new FriendsDrinksBean();
            friendsDrinksBean.setAdminUserId(friendsDrinksState.getFriendsDrinksId().getAdminUserId());
            friendsDrinksBean.setId(keyValue.value.getFriendsDrinksId().getFriendsDrinksId());
            friendsDrinksBean.setName(friendsDrinksState.getName());
            if (friendsDrinksState.getUserIds() != null) {
                friendsDrinksBean.setUserIds(friendsDrinksState.getUserIds().stream().collect(Collectors.toList()));
            }
            friendsDrinksList.add(friendsDrinksBean);
        }
        allKvs.close();
        GetAllFriendsDrinksResponseBean response = new GetAllFriendsDrinksResponseBean();
        response.setFriendsDrinkList(friendsDrinksList);
        return response;
    }

    @GET
    @Path("/users/{userId}/friendsdrinks")
    @Produces(MediaType.APPLICATION_JSON)
    public GetFriendsDrinksResponseBean getFriendsDrinksForUser(@PathParam("userId") final String userId) {
        ReadOnlyKeyValueStore<FriendsDrinksId, andrewgrant.friendsdrinks.avro.FriendsDrinksState> kv =
                kafkaStreams.store(StoreQueryParameters.fromNameAndType(FRIENDSDRINKS_STORE, QueryableStoreTypes.keyValueStore()));
        // TODO(andyg7): this is not efficient! We should have a state store that
        // removes the need for a full scan but for now this is OK.
        KeyValueIterator<FriendsDrinksId, andrewgrant.friendsdrinks.avro.FriendsDrinksState> allKvs = kv.all();
        List<FriendsDrinksBean> adminFriendsDrinks = new ArrayList<>();
        List<FriendsDrinksBean> memberFriendsDrinks = new ArrayList<>();
        while (allKvs.hasNext()) {
            KeyValue<FriendsDrinksId, andrewgrant.friendsdrinks.avro.FriendsDrinksState> keyValue = allKvs.next();
            FriendsDrinksState friendsDrinksState = keyValue.value;
            if (friendsDrinksState.getFriendsDrinksId().getAdminUserId().equals(userId)) {
                FriendsDrinksBean friendsDrinksBean = new FriendsDrinksBean();
                friendsDrinksBean.setAdminUserId(friendsDrinksState.getFriendsDrinksId().getAdminUserId());
                friendsDrinksBean.setId(keyValue.value.getFriendsDrinksId().getFriendsDrinksId());
                friendsDrinksBean.setName(friendsDrinksState.getName());
                if (friendsDrinksState.getUserIds() != null) {
                    friendsDrinksBean.setUserIds(friendsDrinksState.getUserIds().stream().collect(Collectors.toList()));
                }
                adminFriendsDrinks.add(friendsDrinksBean);
            } else {
                List<String> userIds = friendsDrinksState.getUserIds();
                if (userIds.contains(userId)) {
                    FriendsDrinksBean friendsDrinksBean = new FriendsDrinksBean();
                    friendsDrinksBean.setAdminUserId(friendsDrinksState.getFriendsDrinksId().getAdminUserId());
                    friendsDrinksBean.setId(keyValue.value.getFriendsDrinksId().getFriendsDrinksId());
                    friendsDrinksBean.setName(friendsDrinksState.getName());
                    friendsDrinksBean.setUserIds(friendsDrinksState.getUserIds().stream().collect(Collectors.toList()));
                    memberFriendsDrinks.add(friendsDrinksBean);
                }
            }
        }
        allKvs.close();
        GetFriendsDrinksResponseBean response = new GetFriendsDrinksResponseBean();
        response.setAdminFriendsDrinks(adminFriendsDrinks);
        response.setMemberFriendsDrinks(memberFriendsDrinks);
        return response;
    }

    @DELETE
    @Path("/users/{userId}/friendsdrinks/{friendsDrinksId}")
    @Produces(MediaType.APPLICATION_JSON)
    public DeleteFriendsDrinksResponseBean deleteFriendsDrinks(
            @PathParam("userId") String userId,
            @PathParam("friendsDrinksId") String friendsDrinksId) throws InterruptedException {
        final String topicName = envProps.getProperty("friendsdrinks-api.topic.name");
        String requestId = UUID.randomUUID().toString();
        DeleteFriendsDrinksRequest deleteFriendsDrinksRequest = DeleteFriendsDrinksRequest
                .newBuilder()
                .setFriendsDrinksId(
                        FriendsDrinksId
                                .newBuilder()
                                .setFriendsDrinksId(friendsDrinksId)
                                .setAdminUserId(userId)
                                .build())
                .setRequestId(requestId)
                .build();

        FriendsDrinksEvent friendsDrinksEvent = FriendsDrinksEvent
                .newBuilder()
                .setRequestId(deleteFriendsDrinksRequest.getRequestId())
                .setEventType(EventType.DELETE_FRIENDSDRINKS_REQUEST)
                .setDeleteFriendsDrinksRequest(deleteFriendsDrinksRequest)
                .build();
        ProducerRecord<String, FriendsDrinksEvent> producerRecord =
                new ProducerRecord<>(topicName, requestId, friendsDrinksEvent);
        friendsDrinksKafkaProducer.send(producerRecord);

        ReadOnlyKeyValueStore<String, FriendsDrinksEvent> kv =
                kafkaStreams.store(StoreQueryParameters.fromNameAndType(RESPONSES_STORE, QueryableStoreTypes.keyValueStore()));

        FriendsDrinksEvent backendResponse = kv.get(requestId);
        if (backendResponse == null) {
            for (int i = 0; i < 10; i++) {
                if (backendResponse != null) {
                    break;
                }
                // Give the backend some more time.
                Thread.sleep(100);
                backendResponse = kv.get(requestId);
            }
        }
        if (backendResponse == null) {
            throw new RuntimeException(String.format(
                    "Failed to get DeleteFriendsDrinksResponse for request id %s", requestId));
        }
        DeleteFriendsDrinksResponseBean responseBean = new DeleteFriendsDrinksResponseBean();
        Result result = backendResponse.getDeleteFriendsDrinksResponse().getResult();
        responseBean.setResult(result.toString());
        return responseBean;
    }

    @POST
    @Path("/users/{userId}/friendsdrinks/{friendsDrinksId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public PostFriendsDrinksResponseBean updateFriendsDrinks(@PathParam("userId") String userId,
                                                             @PathParam("friendsDrinksId") String friendsDrinksId,
                                                             PostFriendsDrinksRequestBean requestBean)
            throws ExecutionException, InterruptedException {
        final String topicName = envProps.getProperty("friendsdrinks-api.topic.name");
        String requestId = UUID.randomUUID().toString();
        ScheduleType scheduleType = null;
        if (requestBean.getScheduleType() != null) {
            scheduleType = ScheduleType.valueOf(requestBean.getScheduleType());
        }
        FriendsDrinksEvent friendsDrinksEvent;
        FriendsDrinksId friendsDrinksIdAvro = FriendsDrinksId
                .newBuilder()
                .setAdminUserId(userId)
                .setFriendsDrinksId(friendsDrinksId)
                .build();
        if (requestBean.getUpdateType() == null) {
            UpdateFriendsDrinksRequest updateFriendsDrinksRequest = UpdateFriendsDrinksRequest
                    .newBuilder()
                    .setFriendsDrinksId(friendsDrinksIdAvro)
                    .setUpdateType(UpdateType.valueOf(UpdateType.PARTIAL.name()))
                    .setScheduleType(scheduleType)
                    .setCronSchedule(requestBean.getCronSchedule())
                    .setRequestId(requestId)
                    .setName(requestBean.getName())
                    .build();
            friendsDrinksEvent = FriendsDrinksEvent
                    .newBuilder()
                    .setRequestId(updateFriendsDrinksRequest.getRequestId())
                    .setEventType(EventType.UPDATE_FRIENDSDRINKS_REQUEST)
                    .setUpdateFriendsDrinksRequest(updateFriendsDrinksRequest)
                    .build();
        } else if (requestBean.getUpdateType().equals("INVITE_FRIEND")) {
            CreateFriendsDrinksInvitationRequest createFriendsDrinksInvitationRequest =
                    CreateFriendsDrinksInvitationRequest
                            .newBuilder()
                            .setRequestId(requestId)
                            .setFriendsDrinksId(friendsDrinksIdAvro)
                            .setUserId(
                                    andrewgrant.friendsdrinks.api.avro.UserId
                                            .newBuilder()
                                            .setUserId(requestBean.getUserId())
                                            .build())
                            .build();
            friendsDrinksEvent = FriendsDrinksEvent
                    .newBuilder()
                    .setRequestId(createFriendsDrinksInvitationRequest.getRequestId())
                    .setEventType(EventType.CREATE_FRIENDSDRINKS_INVITATION_REQUEST)
                    .setCreateFriendsDrinksInvitationRequest(createFriendsDrinksInvitationRequest)
                    .build();
        } else {
            throw new RuntimeException(String.format("Unknown update type %s", requestBean.getUpdateType()));
        }

        ProducerRecord<String, FriendsDrinksEvent> record =
                new ProducerRecord<>(
                        topicName, requestId, friendsDrinksEvent);
        friendsDrinksKafkaProducer.send(record).get();

        ReadOnlyKeyValueStore<String, FriendsDrinksEvent> kv =
                kafkaStreams.store(StoreQueryParameters.fromNameAndType(RESPONSES_STORE, QueryableStoreTypes.keyValueStore()));

        FriendsDrinksEvent backendResponse = kv.get(requestId);
        if (backendResponse == null) {
            for (int i = 0; i < 10; i++) {
                if (backendResponse != null) {
                    break;
                }
                // Give the backend some more time.
                Thread.sleep(100);
                backendResponse = kv.get(requestId);
            }
        }
        if (backendResponse == null) {
            throw new RuntimeException(String.format(
                    "Failed to get UpdateFriendsDrinksResponse for request id %s", requestId));
        }

        if (requestBean.getUpdateType() != null) {
            PostFriendsDrinksResponseBean responseBean = new PostFriendsDrinksResponseBean();
            responseBean.setResult("SUCCESS");
            return responseBean;
        }

        PostFriendsDrinksResponseBean responseBean = new PostFriendsDrinksResponseBean();
        Result result = backendResponse.getUpdateFriendsDrinksResponse().getResult();
        responseBean.setResult(result.toString());
        return responseBean;
    }

    @POST
    @Path("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public UserResponseBean registerUserEvent(@PathParam("userId") String userId,
                                         UserRequestBean requestBean) throws ExecutionException, InterruptedException {
        final String topicName = envProps.getProperty("user-event.topic.name");
        UserId userIdAvro = UserId.newBuilder().setUserId(userId).build();
        UserEvent userEvent;
        if (requestBean.getEventType().equals("SIGNED_UP")) {
            UserSignedUp userSignedUp = UserSignedUp.newBuilder().setUserId(userIdAvro).build();
            userEvent = UserEvent
                    .newBuilder()
                    .setEventType(andrewgrant.friendsdrinks.user.avro.EventType.SIGNED_UP)
                    .setUserSignedUp(userSignedUp)
                    .setUserId(userIdAvro)
                    .build();
        } else if (requestBean.getEventType().equals("CANCELLED")) {
            userEvent = UserEvent
                    .newBuilder()
                    .setEventType(andrewgrant.friendsdrinks.user.avro.EventType.CANCELLED)
                    .setUserId(userIdAvro)
                    .build();
        } else {
            throw new RuntimeException(String.format("Unknown event type %s", requestBean.getEventType()));
        }
        ProducerRecord<UserId, UserEvent> record = new ProducerRecord<>(
                topicName,
                userEvent.getUserId(),
                userEvent
        );
        userKafkaProducer.send(record).get();
        return new UserResponseBean();
    }

    @POST
    @Path("/users/{userId}/friendsdrinks")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public CreateFriendsDrinksResponseBean createFriendsDrinks(@PathParam("userId") String userId,
                                                               CreateFriendsDrinksRequestBean requestBean)
            throws InterruptedException, ExecutionException {
        final String topicName = envProps.getProperty("friendsdrinks-api.topic.name");
        String requestId = UUID.randomUUID().toString();
        String friendsDrinksId = UUID.randomUUID().toString();
        String scheduleType;
        if (requestBean.getScheduleType() != null) {
            scheduleType = requestBean.getScheduleType();
        } else {
            scheduleType = ScheduleType.ON_DEMAND.name();
        }
        CreateFriendsDrinksRequest createFriendsDrinksRequest = CreateFriendsDrinksRequest
                .newBuilder()
                .setFriendsDrinksId(
                        FriendsDrinksId
                                .newBuilder()
                                .setFriendsDrinksId(friendsDrinksId)
                                .setAdminUserId(userId)
                                .build())
                .setScheduleType(ScheduleType.valueOf(scheduleType))
                .setCronSchedule(requestBean.getCronSchedule())
                .setRequestId(requestId)
                .setName(requestBean.getName())
                .build();
        FriendsDrinksEvent friendsDrinksEvent = FriendsDrinksEvent
                .newBuilder()
                .setRequestId(createFriendsDrinksRequest.getRequestId())
                .setEventType(EventType.CREATE_FRIENDSDRINKS_REQUEST)
                .setCreateFriendsDrinksRequest(createFriendsDrinksRequest)
                .build();
        ProducerRecord<String, FriendsDrinksEvent> record =
                new ProducerRecord<>(topicName, requestId, friendsDrinksEvent);
        friendsDrinksKafkaProducer.send(record).get();

        ReadOnlyKeyValueStore<String, FriendsDrinksEvent> kv =
                kafkaStreams.store(StoreQueryParameters.fromNameAndType(RESPONSES_STORE, QueryableStoreTypes.keyValueStore()));

        FriendsDrinksEvent backendResponse = kv.get(requestId);
        if (backendResponse == null) {
            for (int i = 0; i < 10; i++) {
                if (backendResponse != null) {
                    break;
                }
                // Give the backend some more time.
                Thread.sleep(100);
                backendResponse = kv.get(requestId);
            }
        }
        if (backendResponse == null) {
            throw new RuntimeException(String.format(
                    "Failed to get CreateFriendsDrinksResponse for request id %s", requestId));
        }
        CreateFriendsDrinksResponseBean responseBean = new CreateFriendsDrinksResponseBean();
        Result result = backendResponse.getCreateFriendsDrinksResponse().getResult();
        responseBean.setResult(result.toString());
        responseBean.setFriendsDrinksId(friendsDrinksId);
        return responseBean;
    }
}
