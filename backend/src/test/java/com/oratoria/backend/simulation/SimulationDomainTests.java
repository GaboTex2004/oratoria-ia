package com.oratoria.backend.simulation;

import static org.junit.jupiter.api.Assertions.*;
import com.oratoria.backend.simulation.catalog.SimulationScenarioCatalog;
import com.oratoria.backend.simulation.entity.*;
import org.junit.jupiter.api.Test;

class SimulationDomainTests {
 @Test void catalogContainsOneScenarioForEveryTrainingType(){var catalog=new SimulationScenarioCatalog();assertEquals(7,catalog.list().size());assertEquals(7,catalog.list().stream().map(x->x.trainingType()).distinct().count());}
 @Test void sessionTransitionsWithoutAffectingTrainingSessions(){var session=new SimulationSession(null,"job-interview",com.oratoria.backend.training.TrainingType.INTERVIEW,SimulationDifficulty.BASIC,3);assertEquals(SimulationStatus.ACTIVE,session.getStatus());session.beginProcessing();session.completeTurn(false);assertEquals(1,session.getCurrentTurn());assertEquals(SimulationStatus.ACTIVE,session.getStatus());session.beginProcessing();session.completeTurn(true);assertEquals(SimulationStatus.READY_TO_FINISH,session.getStatus());session.complete("{}",true,82.5);assertEquals(SimulationStatus.COMPLETED,session.getStatus());assertEquals(82.5,session.getOverallScore());}
}
