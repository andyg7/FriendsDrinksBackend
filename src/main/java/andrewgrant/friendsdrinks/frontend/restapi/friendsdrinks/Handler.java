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
import andrewgrant.friendsdrinks.frontend.restapi.friendsdrinks.post.PostUsersRequestBean;
import andrewgrant.friendsdrinks.frontend.restapi.friendsdrinks.post.PostUsersResponseBean;
import andrewgrant.friendsdrinks.user.avro.UserEvent;
import andrewgrant.friendsdrinks.user.avro.UserId;
import andrewgrant.friendsdrinks.user.avro.UserSignedUp;

/**
 * Implements frontend REST API friendsdrinks path.
 */
@Path("")
public class Handler {

    public static final String INVITE_FRIEND = "INVITE_FRIEND";
    public static final String REPLY_TO_INVITATION = "REPLY_TO_INVITATION";
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
        List<FriendsDrinksIdBean> friendsDrinksList = new ArrayList<>();
        while (allKvs.hasNext()) {
            KeyValue<FriendsDrinksId, andrewgrant.friendsdrinks.avro.FriendsDrinksState> keyValue = allKvs.next();
            friendsDrinksList.add(new FriendsDrinksIdBean(
                    keyValue.value.getFriendsDrinksId().getAdminUserId(),
                    keyValue.value.getFriendsDrinksId().getFriendsDrinksId()));
        }
        allKvs.close();
        GetAllFriendsDrinksResponseBean response = new GetAllFriendsDrinksResponseBean();
        response.setFriendsDrinkList(friendsDrinksList);
        return response;
    }

    @GET
    @Path("/friendsdrinks/{friendsDrinksId}")
    @Produces(MediaType.APPLICATION_JSON)
    public GetFriendsDrinksResponseBean getFriendsDrinks(@PathParam("friendsDrinksId") String friendsDrinksId) {
        ReadOnlyKeyValueStore<FriendsDrinksId, andrewgrant.friendsdrinks.avro.FriendsDrinksState> kv =
                kafkaStreams.store(StoreQueryParameters.fromNameAndType(FRIENDSDRINKS_STORE, QueryableStoreTypes.keyValueStore()));
        KeyValueIterator<FriendsDrinksId, andrewgrant.friendsdrinks.avro.FriendsDrinksState> allKvs = kv.all();
        GetFriendsDrinksResponseBean response = null;
        while (allKvs.hasNext()) {
            KeyValue<FriendsDrinksId, andrewgrant.friendsdrinks.avro.FriendsDrinksState> keyValue = allKvs.next();
            FriendsDrinksState friendsDrinksState = keyValue.value;
            andrewgrant.friendsdrinks.avro.FriendsDrinksId friendsDrinksIdAvro = keyValue.value.getFriendsDrinksId();
            if (friendsDrinksIdAvro.getFriendsDrinksId().equals(friendsDrinksId)) {
                response = new GetFriendsDrinksResponseBean();
                response.setAdminUserId(friendsDrinksState.getFriendsDrinksId().getAdminUserId());
                response.setId(keyValue.value.getFriendsDrinksId().getFriendsDrinksId());
                response.setName(friendsDrinksState.getName());
                if (friendsDrinksState.getUserIds() != null) {
                    response.setUserIds(friendsDrinksState.getUserIds().stream().collect(Collectors.toList()));
                }
                break;
            }
        }
        allKvs.close();
        if (response == null) {
            throw new BadRequestException(String.format("%s does not exist", friendsDrinksId));
        }
        return response;
    }

    @GET
    @Path("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public GetUserResponseBean getUser(@PathParam("userId") String userId) {
        GetAllFriendsDrinksForUserResponseBean getAllFriendsDrinksForUserResponseBean =
                getAllFriendsDrinksForUser(userId);
        GetUserResponseBean getUserResponseBean = new GetUserResponseBean();
        getUserResponseBean.setAdminFriendsDrinks(getAllFriendsDrinksForUserResponseBean.getAdminFriendsDrinks());
        getUserResponseBean.setMemberFriendsDrinks(getAllFriendsDrinksForUserResponseBean.getMemberFriendsDrinks());
        ReadOnlyKeyValueStore<FriendsDrinksPendingInvitationId, FriendsDrinksPendingInvitation> kv =
                kafkaStreams.store(StoreQueryParameters.fromNameAndType(PENDING_INVITATIONS_STORE, QueryableStoreTypes.keyValueStore()));
        KeyValueIterator<FriendsDrinksPendingInvitationId, FriendsDrinksPendingInvitation> allKvs = kv.all();

        List<FriendsDrinksInvitationBean> invitationBeans = new ArrayList<>();
        while (allKvs.hasNext()) {
            KeyValue<FriendsDrinksPendingInvitationId, FriendsDrinksPendingInvitation> keyValue = allKvs.next();
            if (keyValue.key.getUserId().getUserId().equals(userId)) {
                FriendsDrinksInvitationBean invitationBean = new FriendsDrinksInvitationBean();
                invitationBean.setAdminUserId(keyValue.value.getFriendsDrinksId().getAdminUserId());
                invitationBean.setFriendsDrinksId(keyValue.value.getFriendsDrinksId().getFriendsDrinksId());
                invitationBean.setMessage(keyValue.value.getMessage());
                invitationBeans.add(invitationBean);
            }
        }
        allKvs.close();
        getUserResponseBean.setInvitations(invitationBeans);

        return getUserResponseBean;
    }

    @GET
    @Path("/users/{userId}/friendsdrinks")
    @Produces(MediaType.APPLICATION_JSON)
    public GetAllFriendsDrinksForUserResponseBean getAllFriendsDrinksForUser(@PathParam("userId") final String userId) {
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
                if (userIds != null && userIds.contains(userId)) {
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
        GetAllFriendsDrinksForUserResponseBean response = new GetAllFriendsDrinksForUserResponseBean();
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
    public PostFriendsDrinksResponseBean postFriendsDrinks(@PathParam("userId") String userId,
                                                           @PathParam("friendsDrinksId") String friendsDrinksId,
                                                           PostFriendsDrinksRequestBean requestBean) throws InterruptedException, ExecutionException {

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


        PostFriendsDrinksResponseBean responseBean = new PostFriendsDrinksResponseBean();
        responseBean.setResult(backendResponse.getUpdateFriendsDrinksResponse().getResult().toString());
        return responseBean;
    }


    @POST
    @Path("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public PostUsersResponseBean postUsers(@PathParam("userId") String userId,
                                           PostUsersRequestBean requestBean)
            throws ExecutionException, InterruptedException {
        final String topicName = envProps.getProperty("friendsdrinks-api.topic.name");
        String requestId = UUID.randomUUID().toString();
        String friendsDrinksId = requestBean.getFriendsDrinksId();
        FriendsDrinksEvent friendsDrinksEvent;
        FriendsDrinksId friendsDrinksIdAvro = FriendsDrinksId
                .newBuilder()
                .setAdminUserId(userId)
                .setFriendsDrinksId(friendsDrinksId)
                .build();
        if (requestBean.getUpdateType().equals(INVITE_FRIEND)) {
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
        } else if (requestBean.getUpdateType().equals(REPLY_TO_INVITATION)) {
            CreateFriendsDrinksInvitationReplyRequest createFriendsDrinksInvitationReplyRequest =
                    CreateFriendsDrinksInvitationReplyRequest.newBuilder()
                            .setFriendsDrinksId(friendsDrinksIdAvro)
                            .setReply(Reply.valueOf(requestBean.getInvitationReply()))
                            .setRequestId(requestId)
                            .build();
            friendsDrinksEvent = FriendsDrinksEvent
                    .newBuilder()
                    .setRequestId(createFriendsDrinksInvitationReplyRequest.getRequestId())
                    .setEventType(EventType.CREATE_FRIENDSDRINKS_INVITATION_REPLY_REQUEST)
                    .setCreateFriendsDrinksInvitationReplyRequest(createFriendsDrinksInvitationReplyRequest)
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


        PostUsersResponseBean responseBean = new PostUsersResponseBean();
        Result result;
        if (requestBean.getUpdateType().equals(INVITE_FRIEND)) {
            result = backendResponse.getCreateFriendsDrinksInvitationResponse().getResult();
        } else if (requestBean.getUpdateType().equals(REPLY_TO_INVITATION)) {
            result = backendResponse.getCreateFriendsDrinksInvitationReplyResponse().getResult();
        } else {
            throw new RuntimeException(String.format("Unexpected update type %s", requestBean.getUpdateType()));
        }
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
