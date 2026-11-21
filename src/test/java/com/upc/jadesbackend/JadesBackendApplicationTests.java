package com.upc.jadesbackend;

import com.upc.jadesbackend.dtos.*;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.services.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jades_test;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create",
        "app.jwt.secret=clave-exclusiva-de-pruebas-locales-de-jades-2026"
})
class JadesBackendApplicationTests {

    @Autowired EntityManager em;
    @Autowired ClienteService clientes;
    @Autowired CondicionCreditoService condiciones;
    @Autowired VentaService ventas;
    @Autowired CreditoRepositorio creditos;
    @Autowired CuotaRepositorio cuotas;
    @Autowired PagoService pagos;

    @ParameterizedTest
    @ValueSource(strings = {"UNICO", "MENSUAL"})
    @Transactional
    void ventaConCuentaCuotasYCobro(String modalidad) {
        Tienda tienda = new Tienda();
        tienda.setNombreTienda("Tienda de prueba");
        tienda.setRazonSocial("Prueba");
        tienda.setDocumentoTienda("20123456789");
        tienda.setDireccion("Local de prueba");
        tienda.setGiroDeNegocio("Comercio");
        tienda.setEstadoTienda("ACTIVO");
        em.persist(tienda);
        Usuario admin = new Usuario();
        admin.setNombreUsuario("Administradora");
        admin.setCorreoUsuario("admin@example.test");
        admin.setPasswordHash("sin-login");
        admin.setActivo(true);
        admin.setRol("ADMIN_NEGOCIO");
        em.persist(admin);
        UsuarioTienda acceso = new UsuarioTienda();
        acceso.setIdUsuario(admin);
        acceso.setIdTienda(tienda);
        acceso.setEstadoAcceso("ACTIVO");
        acceso.setFechaAsignacion(LocalDate.now());
        em.persist(acceso);
        var principal = new UsuarioPrincipal(admin);
        var cliente = clientes.registrar(tienda.getId(),
                new RequestAltaClienteDTO("Cliente", "12345678", null, "cliente@example.test", "Prueba12345"), principal);
        assertEquals("CLIENTE", em.find(Cliente.class, cliente.id()).getIdUsuario().getRol());
        var tasa = new BigDecimal("0.18");
        condiciones.registrar(tienda.getId(), cliente.id(), new RequestCondicionCrediticiaDTO("PEN",
                new BigDecimal("5000"), 12, 20, 26, null, "NOMINAL", tasa, "ANUAL", "MENSUAL",
                "EFECTIVA", tasa, "ANUAL", null), principal);
        Producto producto = new Producto();
        producto.setIdTienda(tienda);
        producto.setNombreProducto("Producto");
        producto.setTipoProducto("PRODUCTO");
        producto.setUnidadMedida("UNIDAD");
        producto.setPrecioContado(new BigDecimal("100"));
        producto.setPrecioCredito(new BigDecimal("100"));
        producto.setModalidad("AMBAS");
        producto.setEstadoProducto("ACTIVO");
        em.persist(producto);
        int plazo = modalidad.equals("UNICO") ? 1 : 3;
        var solicitud = new RequestVentaDTO(cliente.id(), modalidad, plazo, 0,
                List.of(new RequestVentaDTO.Detalle(producto.getId(), BigDecimal.ONE)));
        var venta = ventas.registrar(tienda.getId(), solicitud, principal, "prueba-venta");
        assertEquals(venta.id(), ventas.registrar(tienda.getId(), solicitud, principal, "prueba-venta").id());
        var credito = creditos.findByIdVenta_Id(venta.id()).orElseThrow();
        var cronograma = cuotas.findAllByIdCredito_IdOrderByNumeroCuotaAsc(credito.getId());
        assertEquals(plazo, cronograma.size());
        var primera = cronograma.getFirst();
        pagos.registrar(primera.getId(), new RequestRegistrarPagoDTO(primera.getImporte(),
                LocalDate.now(FacturacionCorteService.ZONA_PERU), "EFECTIVO"), principal);
        em.flush();
        assertEquals("PAGADA", primera.getEstadoCuota());
        assertEquals(0, primera.getSaldoFinal().compareTo(credito.getSaldoCredito()));
    }

}
