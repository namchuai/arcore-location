package uk.co.appoly.sceneform_example.model;

public class Marker {
    private final double longitude;
    private final double latitude;
    private final String title;
    private final LocationType locationType;

    public Marker(double latitude, double longitude, String title, LocationType locationType) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.title = title;
        this.locationType = locationType;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public String getTitle() {
        return title;
    }

    public LocationType getLocationType() {
        return locationType;
    }
}
