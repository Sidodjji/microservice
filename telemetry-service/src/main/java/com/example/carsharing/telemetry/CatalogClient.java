package com.example.carsharing.telemetry;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
@Component
class CatalogClient {
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 private final String base;
 CatalogClient(@Value("${app.fleet-url:http://localhost:8081}") String base) { this.base=base; }
 void requireExisting(Long id) {
  try {
   int status=client.send(HttpRequest.newBuilder(URI.create(base+"/api/cars/"+id)).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();
   if(status==404) throw new CarNotFoundException();
   if(status!=200) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Catalog unavailable");
  } catch(java.io.IOException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Catalog unavailable",e); }
    catch(InterruptedException e) { Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Catalog interrupted",e); }
 }
}