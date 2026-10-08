package com.example.carsharing.telemetry;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CatalogClientTest {
 @Test void mapsRemoteResponses() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/cars/1",e->{e.sendResponseHeaders(200,-1);e.close();});
  server.createContext("/api/cars/2",e->{e.sendResponseHeaders(404,-1);e.close();});
  server.createContext("/api/cars/3",e->{e.sendResponseHeaders(500,-1);e.close();});server.start();
  try {
   var client=new CatalogClient("http://127.0.0.1:"+server.getAddress().getPort());
   assertThatCode(()->client.requireExisting(1L)).doesNotThrowAnyException();
   assertThatThrownBy(()->client.requireExisting(2L)).isInstanceOf(CarNotFoundException.class);
   assertThatThrownBy(()->client.requireExisting(3L)).isInstanceOf(ResponseStatusException.class);
  } finally {server.stop(0);}
 }
 @Test void verifiesCarBeforeSavingLocation() {
  var repo=mock(CarLocationRepository.class);var client=mock(CatalogClient.class);
  doThrow(new CarNotFoundException()).when(client).requireExisting(1L);
  assertThatThrownBy(()->new TelemetryService(repo,client).recordLocation(new LocationRequest(1L,BigDecimal.ZERO,BigDecimal.ZERO))).isInstanceOf(CarNotFoundException.class);
  verifyNoInteractions(repo);
 }
 @Test void updatesExistingLocationInsteadOfAddingHistory() {
  var repo=mock(CarLocationRepository.class);var client=mock(CatalogClient.class);
  var location=mock(CarLocation.class);when(repo.findByCarId(1L)).thenReturn(Optional.of(location));
  new TelemetryService(repo,client).recordLocation(new LocationRequest(1L,BigDecimal.ONE,BigDecimal.TEN));
  verify(location).update(eq(BigDecimal.ONE),eq(BigDecimal.TEN),any());verify(repo,never()).save(any());
 }
}