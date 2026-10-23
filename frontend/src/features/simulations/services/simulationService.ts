import { apiRequest } from '../../../shared/services/api'
import type { SimulationDifficulty, SimulationScenario, SimulationSession, SimulationFeedback } from '../types/simulation'
export const simulationService={
 listScenarios:()=>apiRequest<SimulationScenario[]>('/simulations/scenarios'),
 list:()=>apiRequest<SimulationSession[]>('/simulations'),
 get:(id:string)=>apiRequest<SimulationSession>(`/simulations/${encodeURIComponent(id)}`),
 create:(scenarioId:string,difficulty:SimulationDifficulty)=>apiRequest<SimulationSession>('/simulations',{method:'POST',body:JSON.stringify({scenarioId,difficulty})}),
 respond:(id:string,audio:Blob,durationSeconds:number,key:string)=>{const form=new FormData();form.append('audio',audio,`simulation-turn.${audio.type.includes('ogg')?'ogg':audio.type.includes('mp4')?'m4a':'webm'}`);form.append('durationSeconds',String(durationSeconds));return apiRequest<SimulationSession>(`/simulations/${encodeURIComponent(id)}/respond`,{method:'POST',headers:{'Idempotency-Key':key},body:form})},
 finish:(id:string)=>apiRequest<SimulationSession>(`/simulations/${encodeURIComponent(id)}/finish`,{method:'POST'}),
 feedback:(id:string)=>apiRequest<SimulationFeedback>(`/simulations/${encodeURIComponent(id)}/feedback`),
}
