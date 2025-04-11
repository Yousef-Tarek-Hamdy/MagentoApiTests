import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class MagentoApiTests {

    private static final String BASE_URL = "https://magento.softwaretestingboard.com";
    private static String customerToken;
    private static String customerEmail;
    private static String customerPassword;

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
        customerEmail = "test" + System.currentTimeMillis() + "@example.com";
        customerPassword = "Password1";
        createCustomerAndToken();
    }

    private static void createCustomerAndToken() {
        createCustomer();
        createCustomerToken();
    }

    private static void createCustomer() {
        String requestBody = String.format("""
    {
        "customer": {
            "email": "%s",
            "firstname": "Test",
            "lastname": "User"
        },
        "password": "%s"
    }
    """, customerEmail, customerPassword);

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/rest/V1/customers")
                .then()
                .statusCode(anyOf(is(200), is(201)));
    }

    private static void createCustomerToken() {
        String requestBody = String.format("""
    {
        "username": "%s",
        "password": "%s"
    }
    """, customerEmail, customerPassword);

        customerToken = given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/rest/V1/integration/customer/token")
                .then()
                .statusCode(200)
                .extract()
                .asString()
                .replaceAll("\"", "");
    }

    /**
     * Finds the URL of the "Joust Duffle Bag" product by searching the website.
     * @return The URL of the product, or null if not found.
     */
    private String findProductUrlBySearch() {
        String formKey = getFormKeyFromHomePage();
        if (formKey == null) {
            return null;
        }

        Response searchResponse = executeSearchRequest(formKey);
        return extractProductUrl(searchResponse);
    }

    /**
     * Retrieves the form key from the home page.
     * @return The form key, or null if not found.
     */
    private String getFormKeyFromHomePage() {
        Response response = given().get("/");
        return extractFormKey(response.getBody().asString());
    }

    /**
     * Extracts the form key from the HTML content.
     * @param html The HTML content.
     * @return The form key, or null if not found.
     */
    private String extractFormKey(String html) {
        Document doc = Jsoup.parse(html);
        Elements formKeyElements = doc.select("input[name='form_key']");
        return formKeyElements.isEmpty() ? null : formKeyElements.val();
    }

    /**
     * Executes a catalog search request.
     *
     * @param formKey The form key.
     * @return The search response.
     */
    private Response executeSearchRequest(String formKey) {
        return given()
                .queryParam("q", "Joust Duffle Bag")
                .queryParam("form_key", formKey)
                .when()
                .get("/catalogsearch/result/");
    }

    /**
     * Extracts the product URL from the search response.
     * @param searchResponse The search response.
     * @return The product URL, or null if not found.
     */
    private String extractProductUrl(Response searchResponse) {
        Document doc = Jsoup.parse(searchResponse.getBody().asString());
        Elements productLinks = doc.select(".product-item-name a");
        return productLinks.isEmpty() ? null : Objects.requireNonNull(productLinks.first()).attr("href");
    }

    /**
     * Verifies the HTTP status code of a given URL.
     *
     * @param url The URL to check.
     */
    private void verifyStatusCode(String url) {
        given().get(url).then().statusCode(200);
    }

    // 1. Validating Response Codes
    @Test
    void testValidProductResponse() {
        String productUrl = findProductUrlBySearch();
        assertNotNull(productUrl, "Product URL not found.");
        verifyStatusCode(productUrl);
    }

    @Test
    void testInvalidPostRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/rest/V1/customers")
                .then()
                .statusCode(anyOf(is(400), is(401), is(403)));
    }

    @Test
    void testUnauthorizedAccess() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/rest/V1/customers/me")
                .then()
                .statusCode(401);
    }

    // 2. Verifying API Response Data
    @Test
    void testProductDataStructure() {
        String productUrl = findProductUrlBySearch();
        assertNotNull(productUrl, "Product URL not found.");

        Response productResponse = given().get(productUrl);
        Document doc = Jsoup.parse(productResponse.getBody().asString());

        assertProductNameContains(doc);
        assertPriceFormatIsValid(doc);
    }

    /**
     * Asserts that the product name in the document contains the expected text.
     *
     * @param doc The HTML document.
     */
    private void assertProductNameContains(Document doc) {
        String actualProductName = doc.select(".base").text();
        assertThat(actualProductName, containsString("Joust Duffle Bag"));
    }

    /**
     * Asserts that the price format in the document is valid.
     * @param doc The HTML document.
     */
    private void assertPriceFormatIsValid(Document doc) {
        String priceText = Objects.requireNonNull(doc.select(".price").first()).text();
        assertTrue(priceText.matches("\\$\\d+\\.\\d{2}"), "Price format is incorrect.");
    }

    @Test
    void testProductReviews() {
        String productUrl = findProductUrlBySearch();
        assertNotNull(productUrl, "Product URL not found.");

        Response productResponse = given().get(productUrl);
        Document doc = Jsoup.parse(productResponse.getBody().asString());

        assertReviewItemsExist(doc);
    }

    /**
     * Asserts that review items exist in the document.
     * @param doc The HTML document.
     */
    private void assertReviewItemsExist(Document doc) {
        Elements reviewItems = doc.select(".reviews-actions a");
        assertThat(reviewItems.size(), greaterThan(0));
    }

    @Test
    void testAddToCartFunctionality() {
        String productUrl = findProductUrlBySearch();
        assertNotNull(productUrl, "Product URL not found.");

        Response productResponse = given().get(productUrl);
        Document doc = Jsoup.parse(productResponse.getBody().asString());

        assertAddToCartButtonExists(doc);
    }

    /**
     * Asserts that the "Add to Cart" button exists in the document.
     * @param doc The HTML document.
     */
    private void assertAddToCartButtonExists(Document doc) {
        Elements addToCartButton = doc.select("#product-addtocart-button");
        assertThat(addToCartButton.size(), greaterThan(0));
    }

    @Test
    void testCategoryPage() {
        verifyCategoryPage("/women/tops-women.html");
    }

    /**
     * Verifies that a category page returns a 200 status code and has product items.
     * @param categoryUrl The URL of the category page.
     */
    private void verifyCategoryPage(String categoryUrl) {
        Response categoryResponse = given().get(categoryUrl);
        categoryResponse.then().statusCode(200);

        Document doc = Jsoup.parse(categoryResponse.getBody().asString());
        assertProductItemsExist(doc);
    }

    /**
     * Asserts that product items exist in the document.
     * @param doc The HTML document.
     */
    private void assertProductItemsExist(Document doc) {
        Elements productItems = doc.select(".product-item");
        assertThat(productItems.size(), greaterThan(0));
    }

    @Test
    void testProductComparison() {
        String productUrl = findProductUrlBySearch();
        assertNotNull(productUrl, "Product URL not found.");

        Response productResponse = given().get(productUrl);
        Document doc = Jsoup.parse(productResponse.getBody().asString());

        assertCompareButtonExists(doc);
    }

    /**
     * Asserts that the "Compare" button exists in the document.
     * @param doc The HTML document.
     */
    private void assertCompareButtonExists(Document doc) {
        Elements compareButton = doc.select(".action.tocompare");
        assertThat(compareButton.size(), greaterThan(0));
    }

    @Test
    void testLayeredNavigationFilter() {
        verifyCategoryPage("/women/tops-women.html?price=100-200");
    }

    @Test
    void testProductSorting() {
        verifyCategoryPage("/women/tops-women.html?product_list_order=price");
    }

    @Test
    void testWishlistFunctionalityWithoutAuth() {
        Response wishlistResponse = given().get("/wishlist/");
        wishlistResponse.then().statusCode(200);
        assertTrue(wishlistResponse.getBody().asString().contains("customer/account/login"));
    }

    @Test
    void testProductImageLoading() {
        String productUrl = findProductUrlBySearch();
        assertNotNull(productUrl, "Product URL not found.");

        Response productResponse = given().get(productUrl);
        Document doc = Jsoup.parse(productResponse.getBody().asString());

        String imageUrl = extractFirstImageUrl(doc);
        assertImageUrlIsAccessible(imageUrl);
    }

    /**
     * Extracts the first image URL from the document.
     * @param doc The HTML document.
     * @return The image URL.
     */
    private String extractFirstImageUrl(Document doc) {
        Elements imageElements = doc.select(".product-image-photo");
        assertFalse(imageElements.isEmpty(), "No product images found.");
        return Objects.requireNonNull(imageElements.first()).attr("src");
    }

    /**
     * Asserts that the image URL is accessible.
     * @param imageUrl The image URL.
     */
    private void assertImageUrlIsAccessible(String imageUrl) {
        try {
            //noinspection deprecation
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            assertThat(connection.getResponseCode(), allOf(greaterThan(199), lessThan(300)));
        } catch (IOException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            fail("Image URL is invalid or inaccessible.");
        }
    }

    // 3. Authentication & Authorization
    @Test
    void testCustomerLoginWithValidCredentials() {
        String requestBody = """
        {
            "username": "yousefhamdy32323@gmail.com",
            "password": "Asd123@sd"
        }
        """;

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/rest/V1/integration/customer/token")
                .then()
                .statusCode(200)
                .body(not(emptyOrNullString()));
    }

    @Test
    void testCustomerLoginWithInvalidCredentials() {
        String requestBody = """
        {
            "username": "invalid@example.com",
            "password": "wrongpass"
        }
        """;

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/rest/V1/integration/customer/token")
                .then()
                .statusCode(401);
    }

    @Test
    void testCustomerRegistration() {
        String randomEmail = "testuser" + System.currentTimeMillis() + "@example.com";

        String requestBody = String.format("""
    {
        "customer": {
            "email": "%s",
            "firstname": "Test",
            "lastname": "User"
        },
        "password": "Test@1234"
    }
    """, randomEmail);

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/rest/V1/customers")
                .then()
                .statusCode(anyOf(is(200), is(201)))
                .body("id", notNullValue());
    }

    @Test
    void testAccessProtectedResourceWithoutToken() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/rest/V1/customers/me")
                .then()
                .statusCode(401);
    }

    // 4. Performance & Load Testing
    @Test
    void testProductResponseTime() {
        String productUrl = findProductUrlBySearch();
        assertNotNull(productUrl, "Product URL not found.");

        long responseTime = measureResponseTime(() -> given().get(productUrl));
        System.out.println("Response Time: " + responseTime + "ms");

        assertTrue(responseTime < 5000, "Response time exceeds 5000ms."); // Adjust threshold as needed
    }

    /**
     * Measures the execution time of a given RestAssured request.
     * @param request The RestAssured request to execute.
     * @return The execution time in milliseconds.
     */
    private long measureResponseTime(Runnable request) {
        long startTime = System.currentTimeMillis();
        request.run();
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    @Test
    void testWishlistFunctionalityWithAuth() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .when()
                .get("/wishlist/")
                .then()
                .statusCode(200)
                .body("items", not(emptyArray()));
    }
}