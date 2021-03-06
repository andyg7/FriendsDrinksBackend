package andrewgrant.friendsdrinks.frontend.api.statestorebeans;

import java.util.List;

/**
 * Bean for FriendsDrinksDetailPageMeetup.
 */
public class FriendsDrinksDetailPageMeetupBean {
    private String date;
    List<UserStateBean> userStateList;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public List<UserStateBean> getUserStateList() {
        return userStateList;
    }

    public void setUserStateList(List<UserStateBean> userStateList) {
        this.userStateList = userStateList;
    }
}
