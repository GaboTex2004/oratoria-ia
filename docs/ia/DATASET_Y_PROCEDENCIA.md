# Datos, licencias y preparación

No había datasets de audio ni etiquetas humanas versionados al comenzar. Los 20
ejercicios y 8 escenarios son contenido didáctico de inicialización; no son muestras
de entrenamiento ni historial de usuarios.

## Fuentes examinadas el 2026-11-08

| Fuente oficial | Licencia informada | Relación con el problema | Decisión |
|---|---|---|---|
| [Mozilla Common Voice](https://commonvoice.mozilla.org/lss/datasets) | CC0-1.0 para los conjuntos consultados | Audio español con transcripciones; útil para medir WER, no tiene notas de oratoria | No descargado: no resuelve etiquetas de calidad discursiva |
| [OpenSLR 61](https://www.openslr.org/61) | Consultar LICENSE de cada recurso | Voz latinoamericana para TTS; no es un dataset de puntuación de discursos | No descargado ni redistribuido |
| Recopilación voluntaria OratoriaAI | Consentimiento específico del participante, pendiente | Puede aportar rúbricas humanas alineadas con el proyecto | Plantilla vacía; cero muestras reales recopiladas |

No se ha identificado ni validado un dataset abierto de español con las cuatro
etiquetas exactas de la rúbrica. Esto no prueba que no exista; la búsqueda realizada
no permite justificar entrenamiento supervisado. No se inventaron etiquetas ni se
entrenó un modelo supervisado propio.

## Qué es propio y qué es preentrenado

- Propio: métricas, reglas NLP, combinación de rúbrica, adaptación, contratos,
  persistencia y validación de resultados.
- Preentrenado por terceros: Whisper de OpenAI ejecutado mediante faster-whisper,
  Qwen mediante Ollama y MediaPipe Pose. No se utiliza OpenAI API ni otra inferencia cloud.
- Prueba sintética: voz producida por Windows SAPI y una imagen negra para verificar
  ausencia de persona. No se presentan como grabaciones humanas ni como evaluación
  de precisión del modelo.

## Protocolo de anotación

La plantilla `ai-service/datasets/annotation-template.csv` no contiene filas.
Recopilar solo de adultos voluntarios con consentimiento independiente de la nota
académica, indicando finalidad, conservación, retiro y contacto responsable.
Usar pseudónimos `speaker_id`, nunca nombres, correos ni documentos de identidad.
Guardar el consentimiento firmado fuera del repositorio y audio exclusivamente en
`ai-service/datasets/private/`, excluido de Git.

Dos evaluadores deben puntuar cada criterio según la rúbrica documentada y resolver
desacuerdos antes de generar la fila consensuada. Documentar concordancia entre
evaluadores, acento, condiciones acústicas y límites de representatividad. Nunca
copiar como etiqueta una nota producida por el propio LLM que se quiere evaluar.

`prepare_dataset.py` rechaza consentimiento ausente, rutas externas, etiquetas
inválidas y muestras duplicadas. Asigna partición por hash del hablante (80/10/10
esperado, no garantizado en muestras pequeñas), de manera que un hablante nunca
aparezca en entrenamiento y prueba. No entrena, no rellena etiquetas y avisa si una
partición está vacía. Las pruebas del preparador usan solo metadatos sintéticos.

Antes de entrenar: acordar tamaño muestral, balance y rúbrica con el docente, guardar
un conjunto de prueba cerrado, comparar con una línea base y reportar MAE por
criterio y acuerdo humano. No se reportan MAE, WER ni precisión inexistentes.
