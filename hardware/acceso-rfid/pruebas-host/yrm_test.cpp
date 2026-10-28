// Prueba del protocolo YRM en la computadora, sin placa:
//   g++ -std=c++11 -Wall -o /tmp/yrm_test yrm_test.cpp && /tmp/yrm_test
// Las tramas de ejemplo son lecturas reales del README de Edwin.
#include <cstdio>
#include <cstring>
#include <vector>

#include "../prueba_lector/yrm.h"

static int fallas = 0;

static void revisar(bool ok, const char* que) {
  printf("%s %s\n", ok ? "ok   " : "FALLA", que);
  if (!ok) fallas++;
}

static std::vector<yrm::Trama> leerTodo(yrm::Lector& l, const std::vector<uint8_t>& bytes) {
  std::vector<yrm::Trama> tramas;
  for (uint8_t b : bytes) {
    if (l.alimentar(b)) tramas.push_back(l.trama());
  }
  return tramas;
}

int main() {
  uint8_t orden[16];
  size_t n;

  n = yrm::ordenInventario(orden);
  const uint8_t inventario[] = {0xBB, 0x00, 0x22, 0x00, 0x00, 0x22, 0x7E};
  revisar(n == sizeof(inventario) && memcmp(orden, inventario, n) == 0, "orden de inventario");

  n = yrm::ordenFijarPotencia(20, orden);
  const uint8_t potencia20[] = {0xBB, 0x00, 0xB6, 0x00, 0x02, 0x07, 0xD0, 0x8F, 0x7E};
  revisar(n == sizeof(potencia20) && memcmp(orden, potencia20, n) == 0, "orden fijar potencia 20 dBm");

  yrm::Lector l;
  // Tarjeta + "nada cerca" + pegatina 1 pegadas, como llegan por el puerto.
  std::vector<uint8_t> bytes = {
      0xBB, 0x02, 0x22, 0x00, 0x11, 0xD1, 0x34, 0x00, 0xE2, 0x00, 0x47, 0x12,
      0xC1, 0xA0, 0x60, 0x26, 0x51, 0x32, 0x01, 0x0B, 0x2D, 0x6E, 0x86, 0x7E,
      0xBB, 0x01, 0xFF, 0x00, 0x01, 0x15, 0x16, 0x7E,
      0xBB, 0x02, 0x22, 0x00, 0x11, 0xC7, 0x30, 0x00, 0xE2, 0x80, 0x69, 0x15,
      0x00, 0x00, 0x50, 0x2D, 0x9D, 0xF8, 0x0D, 0x65, 0x77, 0x68, 0x6F, 0x7E};
  std::vector<yrm::Trama> t = leerTodo(l, bytes);
  revisar(t.size() == 3, "tres tramas seguidas");

  yrm::Etiqueta e;
  char hex[2 * yrm::MAX_EPC + 1];
  revisar(yrm::leerEtiqueta(t[0], e), "la tarjeta es aviso de etiqueta");
  yrm::epcHex(e, hex);
  revisar(strcmp(hex, "E2004712C1A060265132010B") == 0, "EPC de la tarjeta");
  revisar(e.rssi == -47, "RSSI de la tarjeta (-47 dBm)");
  revisar(!yrm::leerEtiqueta(t[1], e) && t[1].comando == yrm::CMD_ERROR &&
              t[1].datos[0] == yrm::ERROR_SIN_ETIQUETA,
          "\"nada cerca\" no es etiqueta");
  revisar(yrm::leerEtiqueta(t[2], e), "pegatina 1");
  yrm::epcHex(e, hex);
  revisar(strcmp(hex, "E28069150000502D9DF80D65") == 0, "EPC de la pegatina 1");

  // Un EPC con 0x7E en medio: el código anterior cortaba la trama ahí.
  yrm::Lector l2;
  std::vector<uint8_t> conSieteE = {0xBB, 0x02, 0x22, 0x00, 0x11, 0xC0, 0x30, 0x00,
                                    0xE2, 0x7E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                    0x00, 0x00, 0x00, 0x01, 0xAA, 0xBB};
  uint8_t suma = 0;
  for (size_t i = 1; i < conSieteE.size(); i++) suma += conSieteE[i];
  conSieteE.push_back(suma);
  conSieteE.push_back(0x7E);
  t = leerTodo(l2, conSieteE);
  revisar(t.size() == 1 && yrm::leerEtiqueta(t[0], e), "EPC con 0x7E adentro");

  // Un byte dañado debe descartar la trama sin perder la siguiente.
  yrm::Lector l3;
  std::vector<uint8_t> danada(bytes.begin(), bytes.begin() + 24);
  danada[10] ^= 0xFF;
  danada.insert(danada.end(), bytes.begin() + 24, bytes.end());
  t = leerTodo(l3, danada);
  revisar(t.size() == 2 && l3.descartadas() == 1, "trama dañada se descarta");

  // Respuesta a leer potencia: 20 dBm.
  yrm::Lector l4;
  t = leerTodo(l4, {0xBB, 0x01, 0xB7, 0x00, 0x02, 0x07, 0xD0, 0x91, 0x7E});
  revisar(t.size() == 1 && yrm::potenciaLeida(t[0]) == 20, "respuesta de potencia");

  printf("\n%s\n", fallas ? "HAY FALLAS" : "Todo bien");
  return fallas ? 1 : 0;
}
