package andrewgrant.friendsdrinks.frontend.restapi.friendsdrinks;

/**
 * DTO for CreateFriendsDrinks request.
 */
public class CreateFriendsDrinksRequestBean {
    private String name;

    public CreateFriendsDrinksRequestBean() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
