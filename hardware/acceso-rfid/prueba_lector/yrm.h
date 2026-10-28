// Protocolo serial del módulo YRM100 / YRM1004 (familia M100, chip Impinj/Magic RF).
//
// Trama:  BB | tipo | comando | largo (2 bytes, big endian) | datos... | checksum | 7E
//   tipo:     00 = orden que mandamos, 01 = respuesta, 02 = aviso (etiqueta leída)
//   checksum: suma de tipo + comando + largo + datos, solo el byte bajo
//
// No depende de Arduino para poder probarlo en la computadora (ver pruebas-host/).
#ifndef YRM_H
#define YRM_H

#include <stdint.h>
#include <stddef.h>

namespace yrm {

const uint8_t INICIO = 0xBB;
const uint8_t FIN = 0x7E;

const uint8_t TIPO_ORDEN = 0x00;
const uint8_t TIPO_RESPUESTA = 0x01;
const uint8_t TIPO_AVISO = 0x02;

const uint8_t CMD_INVENTARIO = 0x22;   // una sola lectura
const uint8_t CMD_FIJAR_POTENCIA = 0xB6;
const uint8_t CMD_LEER_POTENCIA = 0xB7;
const uint8_t CMD_ERROR = 0xFF;

const uint8_t ERROR_SIN_ETIQUETA = 0x15;

const size_t MAX_DATOS = 64;   // un EPC de 12 bytes ocupa 17; sobra espacio
const size_t MAX_EPC = 32;

// Arma una orden completa en `salida`. Devuelve cuántos bytes escribió.
inline size_t armarOrden(uint8_t comando, const uint8_t* datos, uint16_t largo, uint8_t* salida) {
  size_t n = 0;
  uint8_t suma = TIPO_ORDEN + comando + (largo >> 8) + (largo & 0xFF);
  salida[n++] = INICIO;
  salida[n++] = TIPO_ORDEN;
  salida[n++] = comando;
  salida[n++] = largo >> 8;
  salida[n++] = largo & 0xFF;
  for (uint16_t i = 0; i < largo; i++) {
    salida[n++] = datos[i];
    suma += datos[i];
  }
  salida[n++] = suma;
  salida[n++] = FIN;
  return n;
}

inline size_t ordenInventario(uint8_t* salida) {
  return armarOrden(CMD_INVENTARIO, 0, 0, salida);
}

inline size_t ordenLeerPotencia(uint8_t* salida) {
  return armarOrden(CMD_LEER_POTENCIA, 0, 0, salida);
}

// El módulo recibe la potencia en centésimas de dBm (20 dBm = 2000).
inline size_t ordenFijarPotencia(uint8_t dbm, uint8_t* salida) {
  uint16_t centesimas = (uint16_t)dbm * 100;
  uint8_t datos[2] = {(uint8_t)(centesimas >> 8), (uint8_t)(centesimas & 0xFF)};
  return armarOrden(CMD_FIJAR_POTENCIA, datos, 2, salida);
}

struct Trama {
  uint8_t tipo;
  uint8_t comando;
  uint16_t largo;
  uint8_t datos[MAX_DATOS];
};

// Lee el puerto byte por byte y avisa cuando junta una trama válida.
// Usa el campo de largo para saber dónde termina, porque el EPC puede traer
// un 0x7E en medio y cortar ahí partiría la trama.
class Lector {
 public:
  Lector() { reiniciar(); }

  // Devuelve true cuando `trama()` tiene una trama completa y con checksum correcto.
  bool alimentar(uint8_t b) {
    switch (paso_) {
      case ESPERA_INICIO:
        if (b == INICIO) paso_ = TIPO;
        return false;
      case TIPO:
        t_.tipo = b;
        suma_ = b;
        paso_ = COMANDO;
        return false;
      case COMANDO:
        t_.comando = b;
        suma_ += b;
        paso_ = LARGO_ALTO;
        return false;
      case LARGO_ALTO:
        t_.largo = (uint16_t)b << 8;
        suma_ += b;
        paso_ = LARGO_BAJO;
        return false;
      case LARGO_BAJO:
        t_.largo |= b;
        suma_ += b;
        if (t_.largo > MAX_DATOS) {
          descartadas_++;
          reiniciar();
          return false;
        }
        leidos_ = 0;
        paso_ = t_.largo ? DATOS : CHECKSUM;
        return false;
      case DATOS:
        t_.datos[leidos_++] = b;
        suma_ += b;
        if (leidos_ == t_.largo) paso_ = CHECKSUM;
        return false;
      case CHECKSUM:
        if (b != suma_) {
          descartadas_++;
          reiniciar();
          // Ese byte pudo ser el inicio de la siguiente trama.
          if (b == INICIO) paso_ = TIPO;
          return false;
        }
        paso_ = CIERRE;
        return false;
      case CIERRE: {
        bool ok = (b == FIN);
        if (!ok) descartadas_++;
        reiniciar();
        return ok;
      }
    }
    return false;
  }

  const Trama& trama() const { return t_; }
  unsigned long descartadas() const { return descartadas_; }

 private:
  enum Paso { ESPERA_INICIO, TIPO, COMANDO, LARGO_ALTO, LARGO_BAJO, DATOS, CHECKSUM, CIERRE };

  void reiniciar() { paso_ = ESPERA_INICIO; }

  Trama t_;
  Paso paso_;
  uint8_t suma_ = 0;
  uint16_t leidos_ = 0;
  unsigned long descartadas_ = 0;
};

struct Etiqueta {
  int8_t rssi;        // dBm, negativo: -45 es cerca, -70 es lejos
  uint8_t epc[MAX_EPC];
  uint8_t largoEpc;
};

// Si la trama es un aviso de etiqueta, llena `e` y devuelve true.
// Datos del aviso: RSSI (1) | PC (2) | EPC (n) | CRC (2)
inline bool leerEtiqueta(const Trama& t, Etiqueta& e) {
  if (t.tipo != TIPO_AVISO || t.comando != CMD_INVENTARIO || t.largo < 6) return false;
  uint8_t largoEpc = t.largo - 5;
  if (largoEpc > MAX_EPC) return false;
  e.rssi = (int8_t)t.datos[0];
  e.largoEpc = largoEpc;
  for (uint8_t i = 0; i < largoEpc; i++) e.epc[i] = t.datos[3 + i];
  return true;
}

// EPC en hexadecimal y mayúsculas, que es como se guardará en GTWeb.
// `salida` necesita 2 * largoEpc + 1 bytes.
inline void epcHex(const Etiqueta& e, char* salida) {
  const char* hex = "0123456789ABCDEF";
  for (uint8_t i = 0; i < e.largoEpc; i++) {
    salida[2 * i] = hex[e.epc[i] >> 4];
    salida[2 * i + 1] = hex[e.epc[i] & 0x0F];
  }
  salida[2 * e.largoEpc] = '\0';
}

// Respuesta a "leer potencia": devuelve los dBm, o -1 si la trama no es esa respuesta.
inline int potenciaLeida(const Trama& t) {
  if (t.tipo != TIPO_RESPUESTA || t.comando != CMD_LEER_POTENCIA || t.largo != 2) return -1;
  return (((uint16_t)t.datos[0] << 8) | t.datos[1]) / 100;
}

}  // namespace yrm

#endif
