# Métricas y rúbrica `oratoria-rubric-v2.0-local`

Todos los índices de calidad son orientativos y provisionales. No se han calibrado
con un dataset de evaluaciones humanas. Las métricas de señal no son diagnósticos.

## Métricas reproducibles

- **Duración:** muestras de audio decodificadas / 16000. El límite real de 300 s se
  aplica durante la decodificación, sin confiar en la duración enviada por el navegador.
- **Palabras:** tokenización de texto transcrito. WPM = 60 × palabras / duración.
- **Tiempo hablado estimado:** unión de intervalos de palabras/segmentos, uniendo
  separaciones de hasta 0.15 s. No es VAD clínico ni tiempo articulatorio exacto.
- **Pausas:** huecos internos >=0.60 s entre intervalos, con suma, máximo y conteo.
  No se cuentan silencios inicial/final ni se juzga toda pausa como un defecto.
- **Muletillas:** reglas españolas contextuales compartidas. Hesitaciones como “eh”
  y marcadores aislados se cuentan; no se penaliza automáticamente “este proyecto”.
  Los errores de transcripción pueden alterar el conteo.
- **RMS relativo:** raíz de la media de muestras normalizadas al cuadrado.
- **Nivel relativo dBFS:** 20 log10(RMS); silencio absoluto devuelve null. No es dB SPL:
  depende de ganancia, micrófono, compresión y distancia. No se compara como volumen físico.
- **Saturación:** proporción de muestras con amplitud absoluta >=0.99.
- **F0 aproximada:** autocorrelación de ventanas Hann de 40 ms cada 20 ms, banda
  80–400 Hz, energía mínima y correlación normalizada >0.65. Solo se informa mediana
  y desviación si hay al menos cinco ventanas fiables. Puede omitir voces fuera de
  esa banda; no permite inferir sexo, identidad, emoción ni patología.

RMS, dBFS y F0 se muestran como indicadores y no se usan para premiar un tipo de voz
o un micrófono. Las pausas y WPM aportan contexto temporal a la evaluación.

## Preprocesamiento y reglas propias

`services/speech/nlp.py` normaliza acentos, tokeniza, separa oraciones y detecta
marcadores de conectores, estructura y evidencia. Es una heurística transparente,
no un modelo de coherencia entrenado. Una estructura implícita puede no contener
los marcadores esperados; esto debe considerarse al interpretar sus índices.

Para una transcripción con evidencia suficiente:

1. Claridad textual T = max(0, 100 − 2 max(0, palabrasPorOración − 25)
   − min(40, 200 × muletillas / palabras)).
2. Ritmo R = max(0, 100 − max(0, 100−WPM, WPM−180)). El intervalo 100–180 es
   una decisión pedagógica provisional, no una norma universal del español.
3. Fluidez F = max(0, 100 − min(40, 200 × muletillas/palabras)
   − min(40, 150 max(0, proporciónDePausas −0.35))).
4. Regla CLARITY = 0.70 T + 0.15 R + 0.15 F.
5. Regla COHERENCE = min(100, 40 + 12 × conectores detectados).
6. Regla ORGANIZATION = 100 × presencia de apertura/desarrollo/cierre / 3.
7. Regla ARGUMENTATION = min(100, 30 + 14 × marcadores de evidencia).

Cada criterio final = **0.70 × juicio semántico local + 0.30 × regla propia**.
El 70/30 es una hipótesis de diseño explícita que necesita validación humana, no un
peso aprendido ni un resultado experimental. La justificación conserva ambos valores.

## Ponderaciones finales por modalidad

| Modalidad | CLARITY | COHERENCE | ORGANIZATION | ARGUMENTATION |
|---|---:|---:|---:|---:|
| PRESENTATION | .25 | .25 | .25 | .25 |
| INTERVIEW | .25 | .25 | .25 | .25 |
| DEBATE | .20 | .25 | .25 | .30 |
| PITCH | .30 | .20 | .25 | .25 |
| PROJECT_DEFENSE | .25 | .25 | .20 | .30 |
| SALES | .25 | .25 | .25 | .25 |
| IMPROVISATION | .25 | .30 | .25 | .20 |

La nota general es la suma ponderada de los criterios, con pesos centralizados en
`rubrics.py`. No se suma de nuevo un score acústico fuera de esta fórmula.
Con menos de 10 palabras reconocidas o menos de 4 s no se solicita evaluación
semántica ni se emite puntuación. No se completa un criterio ausente con 70 puntos.
Las citas y ejemplos literales se verifican contra la transcripción; no se conservan
como citas fragmentos inexistentes producidos por el modelo.

## Simulaciones y adaptación

Las conversaciones requieren dos respuestas para puntuar; CLARITY, RELEVANCE,
THREAD y ADAPTATION pesan .25 cada una. Es un juicio semántico conversacional local
con rango/estructura validados, separado del híbrido de prácticas. No entra en el
dashboard de prácticas y no se presenta como una puntuación acústica.

Adaptación v1: priorizar el criterio reciente más débil. BASIC sin historial o si el
último score <55; ADVANCED tras tres scores consecutivos >=80; INTERMEDIATE en otro
caso. Si no hay un ejercicio del nivel exacto, se muestran los disponibles indicando
el nivel real. No se inventan resultados históricos ni se atribuye aprendizaje supervisado.

## Indicadores corporales

Presencia = fotogramas con cabeza/hombros visibles (visibilidad >=.6) / fotogramas.
Inclinación de hombros = atan2(|Δy en píxeles|, |Δx en píxeles|). Desplazamiento de
cabeza = ángulo entre nariz y centro de hombros respecto a la vertical de imagen.
Movimiento = media de distancias entre centros de hombros o muñecas en coordenadas
normalizadas de fotogramas sucesivos. No equivale a movimiento físico en metros.
Con detección insuficiente las magnitudes son null. Cámara inclinada, encuadre,
oclusión y perspectiva afectan los resultados. No se mide mirada exacta ni emociones.
