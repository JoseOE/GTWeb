package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.model.Payment;
import com.gymtrack.model.Pedido;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.UserRepository;
import com.itextpdf.barcodes.Barcode128;
import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.List;
import com.itextpdf.layout.element.ListItem;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

// Recibos en PDF con iText: compras de la tienda (carta), tickets del
// mostrador (80 mm), fichas de pago Paynet y recibos de las mensualidades que
// el dueño registra a mano. Todos llevan la leyenda "Comprobante simulado, sin
// validez fiscal": los pagos de GymTrack son de prueba y no hay facturación.
//
// Los precios ya incluyen IVA; el recibo lo desglosa (16 %) para que se vea
// cuánto es impuesto.
@Service
public class ReciboService {

    private static final Logger log = LoggerFactory.getLogger(ReciboService.class);
    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");
    private static final ZoneId ZONA_MX = ZoneId.of("America/Mexico_City");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", ES_MX);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES_MX);
    private static final String LEYENDA = "Comprobante simulado, sin validez fiscal.";

    // Paleta de la marca (css/editorial.css).
    private static final Color TINTA = new DeviceRgb(0x20, 0x22, 0x1f);
    private static final Color MARCA = new DeviceRgb(0xb7, 0x35, 0x11);
    private static final Color GRIS = new DeviceRgb(0x62, 0x64, 0x5d);
    private static final Color LINEA = new DeviceRgb(0xd5, 0xd6, 0xcc);
    private static final Color PAPEL = new DeviceRgb(0xf4, 0xf2, 0xeb);
    // 80 mm en puntos de PDF (1 mm = 2.8346 pt).
    private static final float ANCHO_TICKET = 226.77f;

    private final GymRepository gymRepository;
    private final UserRepository userRepository;

    public ReciboService(GymRepository gymRepository, UserRepository userRepository) {
        this.gymRepository = gymRepository;
        this.userRepository = userRepository;
    }

    // ═══════════════════════════ COMPRAS ═══════════════════════════

    public byte[] reciboPedido(Pedido p) {
        return generar(PageSize.LETTER, 40, (doc, pdf, f) -> {
            Gym gym = gym(p.getGymId());
            encabezado(doc, pdf, f, gym, "RECIBO DE COMPRA", "Pedido #" + p.getFolio(),
                    fechaHora(p.getPagadoEn() != null ? p.getPagadoEn() : p.getCreadoEn()), estadoTexto(p.getEstado()));

            doc.add(new Paragraph()
                    .add(new Text("Cliente: ").setFont(f.negrita()))
                    .add(cliente(p))
                    .setFontSize(10).setMarginTop(14).setMarginBottom(2));
            doc.add(new Paragraph()
                    .add(new Text("Entrega: ").setFont(f.negrita()))
                    .add(Pedido.CANAL_MOSTRADOR.equals(p.getCanal())
                            ? "Entregado en el mostrador de " + nombre(gym)
                            : "Recoger en " + nombre(gym) + (gym != null && gym.getDireccion() != null ? " · " + gym.getDireccion() : ""))
                    .setFontSize(10).setMarginBottom(12));

            Table partidas = new Table(UnitValue.createPercentArray(new float[]{10, 54, 18, 18})).useAllAvailableWidth();
            for (String titulo : new String[]{"Cant.", "Descripción", "Precio", "Importe"}) {
                partidas.addHeaderCell(new Cell().add(new Paragraph(titulo).setFont(f.negrita()).setFontSize(9).setFontColor(ColorConstants.WHITE))
                        .setBackgroundColor(TINTA).setBorder(Border.NO_BORDER).setPadding(6)
                        .setTextAlignment(titulo.equals("Descripción") ? TextAlignment.LEFT : TextAlignment.RIGHT));
            }
            for (Pedido.Partida x : p.getPartidas()) {
                Paragraph descripcion = new Paragraph(x.getTitulo()).setFontSize(10);
                String variante = varianteVisible(x);
                if (variante != null) descripcion.add(new Text("\n" + variante).setFontSize(8.5f).setFontColor(GRIS));
                partidas.addCell(celda(String.valueOf(x.getCantidad()), TextAlignment.RIGHT));
                partidas.addCell(new Cell().add(descripcion).setBorder(Border.NO_BORDER)
                        .setBorderBottom(new SolidBorder(LINEA, 0.6f)).setPadding(6));
                partidas.addCell(celda(dinero(x.getPrecioUnitario()), TextAlignment.RIGHT));
                partidas.addCell(celda(dinero(x.getTotal()), TextAlignment.RIGHT));
            }
            doc.add(partidas);
            doc.add(totales(f, p.getSubtotal(), p.getIva(), p.getTotal()).setMarginTop(8));

            doc.add(new Paragraph().add(new Text("Pago: ").setFont(f.negrita())).add(PedidoService.detallePago(p))
                    .setFontSize(10).setMarginTop(14));
            pie(doc, pdf, f, "GymTrack · " + nombre(gym) + " · Pedido #" + p.getFolio() + " · " + p.getOrderId(), 80);
        });
    }

    // Ticket de 80 mm para la impresora térmica del mostrador. El alto se
    // calcula con el número de partidas para que no quede una hoja en blanco.
    public byte[] ticketPedido(Pedido p) {
        float alto = 300 + p.getPartidas().size() * 30f;
        return generar(new PageSize(ANCHO_TICKET, alto), 12, (doc, pdf, f) -> {
            Gym gym = gym(p.getGymId());
            doc.add(new Paragraph(nombre(gym)).setFont(f.negrita()).setFontSize(12).setTextAlignment(TextAlignment.CENTER).setMarginBottom(0));
            if (gym != null && gym.getDireccion() != null) {
                doc.add(new Paragraph(gym.getDireccion()).setFontSize(7.5f).setFontColor(GRIS).setTextAlignment(TextAlignment.CENTER).setMarginTop(0));
            }
            doc.add(new Paragraph("Ticket #" + p.getFolio() + "\n" + fechaHora(p.getPagadoEn() != null ? p.getPagadoEn() : p.getCreadoEn()))
                    .setFontSize(8.5f).setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("Cliente: " + cliente(p)).setFontSize(8));
            doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.DashedLine(0.6f)));
            for (Pedido.Partida x : p.getPartidas()) {
                String variante = varianteVisible(x);
                Table fila = new Table(UnitValue.createPercentArray(new float[]{68, 32})).useAllAvailableWidth();
                fila.addCell(new Cell().add(new Paragraph(x.getCantidad() + " × " + x.getTitulo()
                                + (variante != null ? "\n   " + variante : "")).setFontSize(8))
                        .setBorder(Border.NO_BORDER).setPadding(1));
                fila.addCell(new Cell().add(new Paragraph(dinero(x.getTotal())).setFontSize(8).setTextAlignment(TextAlignment.RIGHT))
                        .setBorder(Border.NO_BORDER).setPadding(1));
                doc.add(fila);
            }
            doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.DashedLine(0.6f)));
            doc.add(totales(f, p.getSubtotal(), p.getIva(), p.getTotal(), true));
            Map<String, Object> d = p.getDatosPago() == null ? Map.of() : p.getDatosPago();
            if (d.get("recibido") != null) {
                doc.add(new Paragraph("Efectivo recibido: " + dinero(numero(d.get("recibido")))
                        + "\nCambio: " + dinero(numero(d.get("cambio")))).setFontSize(8.5f).setFont(f.negrita()));
            } else {
                doc.add(new Paragraph("Pago: " + PedidoService.detallePago(p)).setFontSize(8));
            }
            Image qr = qr(pdf, "GymTrack · Ticket #" + p.getFolio() + " · " + p.getOrderId(), 70);
            qr.setHorizontalAlignment(HorizontalAlignment.CENTER);
            doc.add(qr);
            doc.add(new Paragraph("¡Gracias por tu compra!\n" + LEYENDA).setFontSize(7).setFontColor(GRIS).setTextAlignment(TextAlignment.CENTER));
        });
    }

    // ═══════════════════════════ PAYNET ═══════════════════════════

    public byte[] fichaPaynet(Pedido p) {
        String referencia = referencia(p);
        return generar(PageSize.LETTER, 40, (doc, pdf, f) -> {
            Gym gym = gym(p.getGymId());
            encabezado(doc, pdf, f, gym, "FICHA DE PAGO EN EFECTIVO", "Pedido #" + p.getFolio(),
                    fechaHora(p.getCreadoEn()), "Pendiente de pago");

            doc.add(new Paragraph("Paynet · simulación").setFont(f.negrita()).setFontSize(10).setFontColor(MARCA).setMarginTop(18).setMarginBottom(0));
            doc.add(new Paragraph(dinero(p.getTotal())).setFont(f.negrita()).setFontSize(34).setFontColor(TINTA).setMarginTop(0).setMarginBottom(0));
            doc.add(new Paragraph("Monto exacto a pagar en efectivo").setFontSize(10).setFontColor(GRIS).setMarginTop(0));

            Barcode128 barras = new Barcode128(pdf);
            barras.setCodeType(Barcode128.CODE128);
            barras.setCode(referencia);
            barras.setBarHeight(56);
            barras.setX(1.4f);
            barras.setFont(null);
            Image codigo = new Image(barras.createFormXObject(TINTA, TINTA, pdf)).setMarginTop(14);
            doc.add(codigo);
            doc.add(new Paragraph(PedidoService.referenciaLegible(referencia)).setFont(f.negrita()).setFontSize(18)
                    .setCharacterSpacing(1.5f).setMarginTop(6).setMarginBottom(0));
            doc.add(new Paragraph("Referencia").setFontSize(9).setFontColor(GRIS).setMarginTop(0));

            Object vence = p.getDatosPago() == null ? null : p.getDatosPago().get("vence");
            if (vence != null) {
                doc.add(new Paragraph().add(new Text("Paga antes del: ").setFont(f.negrita()))
                        .add(fechaHora(OffsetDateTime.parse(vence.toString()).toInstant()))
                        .setFontSize(11).setMarginTop(12));
            }

            doc.add(new Paragraph("Cómo pagar").setFont(f.negrita()).setFontSize(12).setMarginTop(16).setMarginBottom(4));
            List pasos = new List(com.itextpdf.layout.properties.ListNumberingType.DECIMAL).setFontSize(10);
            pasos.add(new ListItem("Acude a cualquier tienda de la red Paynet."));
            pasos.add(new ListItem("Indica en caja que vas a pagar un servicio Paynet."));
            pasos.add(new ListItem("Muestra el código de barras o dicta la referencia."));
            pasos.add(new ListItem("Paga el monto exacto en efectivo y conserva tu comprobante."));
            pasos.add(new ListItem("Recibirás un correo cuando " + nombre(gym) + " reciba tu pago."));
            doc.add(pasos);

            doc.add(new Paragraph("Dónde pagar (red simulada)").setFont(f.negrita()).setFontSize(12).setMarginTop(14).setMarginBottom(4));
            doc.add(new Paragraph("Tiendas de conveniencia · Farmacias · Supermercados · Tiendas departamentales")
                    .setFontSize(10).setFontColor(GRIS));
            doc.add(new Paragraph("Esta ficha es una simulación para pruebas: no la presentes en una tienda real.")
                    .setFont(f.negrita()).setFontSize(10).setFontColor(MARCA).setBackgroundColor(PAPEL).setPadding(8).setMarginTop(10));
            pie(doc, pdf, f, "GymTrack · Ficha Paynet · Pedido #" + p.getFolio() + " · " + referencia, 70);
        });
    }

    // Código de barras de la referencia como PNG, para la web y la app.
    public byte[] codigoPaynetPng(Pedido p) {
        Barcode128 barras = new Barcode128(null, null);
        barras.setCodeType(Barcode128.CODE128);
        barras.setCode(referencia(p));
        java.awt.Image awt = barras.createAwtImage(java.awt.Color.BLACK, java.awt.Color.WHITE);
        // La imagen de iText mide 1 px por módulo: se amplía sin suavizar para que
        // las barras queden nítidas en pantalla y el lector las distinga.
        int escala = 3;
        int margen = 20;
        int ancho = awt.getWidth(null) * escala + margen * 2;
        int alto = 120;
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, ancho, alto);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(awt, margen, 10, awt.getWidth(null) * escala, alto - 20, null);
        g.dispose();
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo dibujar el código de barras", e);
        }
    }

    // ═══════════════════════════ MENSUALIDADES ═══════════════════════════

    public byte[] reciboPago(Payment pago) {
        return generar(PageSize.LETTER, 40, (doc, pdf, f) -> {
            Gym gym = gym(pago.getGymId());
            User miembro = pago.getUserId() == null ? null : userRepository.findById(pago.getUserId()).orElse(null);
            String folio = pago.getId() == null ? "" : pago.getId().substring(Math.max(0, pago.getId().length() - 8)).toUpperCase();
            encabezado(doc, pdf, f, gym, "RECIBO DE MEMBRESÍA", "Folio " + folio,
                    pago.getFechaPago() == null ? "" : pago.getFechaPago().format(FECHA), "Pagado");

            doc.add(new Paragraph().add(new Text("Miembro: ").setFont(f.negrita()))
                    .add(miembro == null ? "—" : nombreDe(miembro) + " · " + miembro.getEmail())
                    .setFontSize(10).setMarginTop(14));

            String concepto = pago.getPlan() != null ? "Plan " + pago.getPlan() : "Mensualidad";
            if (pago.getDuracionUnidad() != null && pago.getDuracionCantidad() != null) {
                concepto += " (" + CatalogoService.duracionTexto(new PlanPagado(null, null, pago.getDuracionUnidad(), pago.getDuracionCantidad())) + ")";
            }
            Table detalle = new Table(UnitValue.createPercentArray(new float[]{70, 30})).useAllAvailableWidth().setMarginTop(10);
            detalle.addHeaderCell(new Cell().add(new Paragraph("Concepto").setFont(f.negrita()).setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(TINTA).setBorder(Border.NO_BORDER).setPadding(6));
            detalle.addHeaderCell(new Cell().add(new Paragraph("Importe").setFont(f.negrita()).setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(TINTA).setBorder(Border.NO_BORDER).setPadding(6).setTextAlignment(TextAlignment.RIGHT));
            Paragraph descripcion = new Paragraph(concepto).setFontSize(10);
            if (pago.getCubreHasta() != null) {
                descripcion.add(new Text("\nCubre hasta el " + pago.getCubreHasta().format(FECHA)).setFontSize(8.5f).setFontColor(GRIS));
            }
            detalle.addCell(new Cell().add(descripcion).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(LINEA, 0.6f)).setPadding(6));
            double monto = pago.getMonto() == null ? 0 : pago.getMonto();
            detalle.addCell(celda(dinero(monto), TextAlignment.RIGHT));
            doc.add(detalle);

            double subtotal = Math.round(monto / 1.16 * 100) / 100.0;
            doc.add(totales(f, subtotal, monto - subtotal, monto).setMarginTop(8));
            doc.add(new Paragraph().add(new Text("Pago: ").setFont(f.negrita()))
                    .add(pago.getMetodo() == null ? "—" : pago.getMetodo()).setFontSize(10).setMarginTop(14));
            if (pago.getNota() != null && !pago.getNota().isBlank()) {
                doc.add(new Paragraph().add(new Text("Nota: ").setFont(f.negrita())).add(pago.getNota()).setFontSize(10));
            }
            pie(doc, pdf, f, "GymTrack · " + nombre(gym) + " · Recibo de membresía " + folio, 80);
        });
    }

    // ═══════════════════════════ PIEZAS COMUNES ═══════════════════════════

    private record Fuentes(PdfFont normal, PdfFont negrita) {}

    private interface Contenido {
        void dibujar(Document doc, PdfDocument pdf, Fuentes f) throws IOException;
    }

    private byte[] generar(PageSize pagina, float margen, Contenido contenido) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfDocument pdf = new PdfDocument(new PdfWriter(salida));
            pdf.getDocumentInfo().setCreator("GymTrack");
            Document doc = new Document(pdf, pagina);
            doc.setMargins(margen, margen, margen, margen);
            Fuentes f = new Fuentes(PdfFontFactory.createFont(StandardFonts.HELVETICA),
                    PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD));
            doc.setFont(f.normal()).setFontColor(TINTA);
            contenido.dibujar(doc, pdf, f);
            doc.close();
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
    }

    private void encabezado(Document doc, PdfDocument pdf, Fuentes f, Gym gym, String titulo, String folio,
                            String fecha, String estado) {
        Image logo = logo(gym);
        Table t = new Table(UnitValue.createPercentArray(logo != null ? new float[]{12, 50, 38} : new float[]{62, 38})).useAllAvailableWidth();
        if (logo != null) {
            t.addCell(new Cell().add(logo).setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE).setPaddingLeft(0));
        }
        Paragraph datos = new Paragraph(nombre(gym)).setFont(f.negrita()).setFontSize(15).setMarginBottom(0);
        if (gym != null) {
            StringBuilder extra = new StringBuilder();
            if (gym.getDireccion() != null && !gym.getDireccion().isBlank()) extra.append(gym.getDireccion());
            if (gym.getTelefono() != null && !gym.getTelefono().isBlank()) extra.append(extra.isEmpty() ? "" : "\n").append("Tel. ").append(gym.getTelefono());
            if (!extra.isEmpty()) datos.add(new Text("\n" + extra).setFont(f.normal()).setFontSize(8.5f).setFontColor(GRIS));
        }
        t.addCell(new Cell().add(datos).setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE));
        Paragraph derecha = new Paragraph(titulo).setFont(f.negrita()).setFontSize(11).setFontColor(MARCA).setMarginBottom(2)
                .add(new Text("\n" + folio).setFontColor(TINTA).setFontSize(13))
                .add(new Text("\n" + fecha).setFont(f.normal()).setFontColor(GRIS).setFontSize(8.5f))
                .add(new Text("\n" + estado).setFontColor(TINTA).setFontSize(9));
        t.addCell(new Cell().add(derecha.setTextAlignment(TextAlignment.RIGHT)).setBorder(Border.NO_BORDER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE));
        doc.add(t);
        com.itextpdf.kernel.pdf.canvas.draw.SolidLine linea = new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1.5f);
        linea.setColor(MARCA);
        doc.add(new LineSeparator(linea).setMarginTop(8));
    }

    private Table totales(Fuentes f, Double subtotal, Double iva, Double total) {
        return totales(f, subtotal, iva, total, false);
    }

    // En el ticket (compacto) los totales ocupan todo el ancho y van en letra chica.
    private Table totales(Fuentes f, Double subtotal, Double iva, Double total, boolean compacto) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .setWidth(UnitValue.createPercentValue(compacto ? 100 : 45))
                .setHorizontalAlignment(HorizontalAlignment.RIGHT);
        filaTotal(t, f, "Subtotal (sin IVA)", subtotal, false, compacto);
        filaTotal(t, f, "IVA 16 %", iva, false, compacto);
        filaTotal(t, f, "Total", total, true, compacto);
        return t;
    }

    private void filaTotal(Table t, Fuentes f, String etiqueta, Double monto, boolean fuerte, boolean compacto) {
        float tam = compacto ? (fuerte ? 10 : 8) : (fuerte ? 11 : 9.5f);
        Paragraph a = new Paragraph(etiqueta).setFontSize(tam);
        Paragraph b = new Paragraph(dinero(monto)).setFontSize(tam).setTextAlignment(TextAlignment.RIGHT);
        if (fuerte) { a.setFont(f.negrita()); b.setFont(f.negrita()); }
        Cell ca = new Cell().add(a).setBorder(Border.NO_BORDER).setPadding(2);
        Cell cb = new Cell().add(b).setBorder(Border.NO_BORDER).setPadding(2);
        if (fuerte) {
            ca.setBorderTop(new SolidBorder(TINTA, 1)).setPaddingTop(5);
            cb.setBorderTop(new SolidBorder(TINTA, 1)).setPaddingTop(5);
        }
        t.addCell(ca);
        t.addCell(cb);
    }

    // QR con el folio y la leyenda de comprobante simulado.
    private void pie(Document doc, PdfDocument pdf, Fuentes f, String textoQr, float tamQr) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{20, 80})).useAllAvailableWidth().setMarginTop(24);
        t.addCell(new Cell().add(qr(pdf, textoQr, tamQr)).setBorder(Border.NO_BORDER).setPaddingLeft(0));
        t.addCell(new Cell().add(new Paragraph(LEYENDA).setFont(f.negrita()).setFontSize(10).setMarginBottom(2)
                        .add(new Text("\nGenerado por GymTrack el " + fechaHora(Instant.now()) + ".").setFont(f.normal()).setFontSize(8).setFontColor(GRIS)))
                .setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE));
        doc.add(t);
    }

    private Image qr(PdfDocument pdf, String texto, float tam) {
        return new Image(new BarcodeQRCode(texto).createFormXObject(TINTA, pdf)).scaleToFit(tam, tam);
    }

    private Cell celda(String texto, TextAlignment alineacion) {
        return new Cell().add(new Paragraph(texto).setFontSize(10).setTextAlignment(alineacion))
                .setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(LINEA, 0.6f)).setPadding(6);
    }

    // El logo del gimnasio llega como data URI (lo reduce el panel a 512 px).
    private Image logo(Gym gym) {
        if (gym == null || gym.getLogo() == null || !gym.getLogo().startsWith("data:image/")) return null;
        try {
            byte[] datos = Base64.getDecoder().decode(gym.getLogo().substring(gym.getLogo().indexOf(',') + 1));
            return new Image(ImageDataFactory.create(datos)).scaleToFit(52, 52);
        } catch (Exception e) {
            log.warn("No se pudo usar el logo del gimnasio {} en el recibo: {}", gym.getId(), e.getMessage());
            return null;
        }
    }

    private Gym gym(String gymId) {
        return gymId == null ? null : gymRepository.findById(gymId).orElse(null);
    }

    private static String nombre(Gym gym) {
        return gym == null || gym.getNombre() == null || gym.getNombre().isBlank() ? "GymTrack" : gym.getNombre();
    }

    private String cliente(Pedido p) {
        if (p.getUserId() != null) {
            User u = userRepository.findById(p.getUserId()).orElse(null);
            if (u != null) return nombreDe(u) + " · " + u.getEmail();
        }
        return p.getCliente() != null ? p.getCliente() : (p.getEmail() == null ? "Público en general" : p.getEmail());
    }

    private static String nombreDe(User u) {
        return u.getNombre() == null || u.getNombre().isBlank() ? u.getEmail() : u.getNombre();
    }

    // "Plan" es el nombre interno de la única variante de un plan; "Única", el
    // de un producto sin presentaciones: ninguno le dice nada al cliente.
    private static String varianteVisible(Pedido.Partida x) {
        String v = x.getVariante();
        return v == null || v.isBlank() || "Plan".equals(v) || "Única".equals(v) ? null : v;
    }

    private static String referencia(Pedido p) {
        Object r = p.getDatosPago() == null ? null : p.getDatosPago().get("referencia");
        if (r == null) throw new TiendaException(org.springframework.http.HttpStatus.NOT_FOUND, "Este pedido no tiene ficha Paynet.");
        return r.toString();
    }

    private static String estadoTexto(String estado) {
        if (estado == null) return "";
        return switch (estado) {
            case Pedido.ESTADO_PAGADO -> "Pagado";
            case Pedido.ESTADO_PENDIENTE_PAGO -> "Pendiente de pago";
            case Pedido.ESTADO_CANCELADO -> "Cancelado";
            case Pedido.ESTADO_REEMBOLSADO -> "Reembolsado";
            default -> estado;
        };
    }

    private static String fechaHora(Instant instante) {
        return instante == null ? "" : instante.atZone(ZONA_MX).format(FECHA_HORA);
    }

    static String fecha(LocalDate fecha) {
        return fecha == null ? "" : fecha.format(FECHA);
    }

    private static double numero(Object valor) {
        return valor instanceof Number n ? n.doubleValue() : 0;
    }

    private static String dinero(Double monto) {
        return String.format(ES_MX, "$%,.2f", monto == null ? 0 : monto);
    }
}
