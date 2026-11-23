# Accesibilidad

WCAG 2.2 AA orienta el diseño junto con las recomendaciones para Android Views y con el marco de inclusión de la Ley 20.422.

- El texto usa SP y respeta el factor del sistema. **Grande** y **Muy grande** amplían ese factor. Formularios, paneles y detalles permiten desplazamiento. Cuando el tamaño efectivo supera 1,4 veces, el menú inferior usa una lista con etiquetas completas.
- La interfaz usa etiquetas, cabeceras accesibles y regiones de anuncio para errores y estados. Los controles principales combinan texto, iconos y objetivos táctiles de 48 dp.
- Los estados se expresan con texto. SOS requiere confirmación y el formulario pide confirmación si se descartan cambios.
- El tema oscuro puede seguir la configuración del teléfono. Alto contraste refuerza texto secundario, bordes y contornos.

| Combinación base | Claro | Oscuro |
| --- | ---: | ---: |
| Texto principal sobre fondo | 15,43:1 | 16,15:1 |
| Texto secundario sobre su superficie | 6,96:1 | 9,28:1 |
| Texto de botón primario | 6,38:1 | 7,47:1 |
| Texto de botón SOS | 6,57:1 | 7,71:1 |

Las cifras se calcularon sobre las parejas RGB declaradas. El protocolo requiere revisar foco, TalkBack, Scanner, fuente ampliada, teclado, estados y orientación. Consulta [resultados](RESULTADOS_PRUEBAS.md) para conocer las pruebas ejecutadas.

[WCAG 2.2](https://www.w3.org/TR/WCAG22/) · [Android Views](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views) · [Ley 20.422](https://www.bcn.cl/leychile/navegar?idNorma=1010903) · [SENADIS](https://www.senadis.gob.cl/)
