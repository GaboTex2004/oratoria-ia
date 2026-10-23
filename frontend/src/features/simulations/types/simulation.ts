export type SimulationDifficulty = 'BASIC' | 'INTERMEDIATE' | 'ADVANCED'
export type SimulationStatus = 'ACTIVE' | 'PROCESSING' | 'READY_TO_FINISH' | 'COMPLETED' | 'FAILED'
export type SimulationSpeaker = 'USER' | 'AI'
export interface SimulationScenario { id:string; title:string; description:string; trainingType:string; aiRole:string; difficulties:SimulationDifficulty[] }
export interface SimulationTurn { id:string; sequenceNumber:number; speaker:SimulationSpeaker; status:'PROCESSING'|'COMPLETED'|'FAILED'; text:string|null; hasAudio:boolean; audioDurationSeconds:number|null; errorCode:string|null; errorMessage:string|null; createdAt:string }
export interface SimulationFeedback { sufficientEvidence:boolean; insufficientEvidenceReason:string|null; overallScore:number|null; summary:string; strengths:string[]; areasForImprovement:string[]; recommendations:string[]; criteriaScores:Record<string,number> }
export interface SimulationSession { id:string; scenarioId:string; scenarioTitle:string; trainingType:string; difficulty:SimulationDifficulty; status:SimulationStatus; currentTurn:number; maxTurns:number; turns:SimulationTurn[]; feedback:SimulationFeedback|null; errorCode:string|null; errorMessage:string|null; createdAt:string; completedAt:string|null }
export const DIFFICULTY_LABELS:Record<SimulationDifficulty,string>={BASIC:'Básica',INTERMEDIATE:'Intermedia',ADVANCED:'Avanzada'}
export function canRespond(status:SimulationStatus){return status==='ACTIVE'}
export function canFinish(session:Pick<SimulationSession,'status'|'currentTurn'>){return session.currentTurn>0&&(session.status==='ACTIVE'||session.status==='READY_TO_FINISH')}
export function newIdempotencyKey(){return typeof crypto!=='undefined'&&crypto.randomUUID?crypto.randomUUID():`${Date.now()}-${Math.random().toString(16).slice(2)}`}
