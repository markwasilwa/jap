package biz.duka;

public class Company {

    private String name;
    private String email;

    @BuildProperty
    public void setName(String name) {
        this.name = name;
    }

    @BuildProperty
    public void setEmail(String email) {
        this.email = email;
    }
}
