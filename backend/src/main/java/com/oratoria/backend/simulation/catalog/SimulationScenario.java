package com.oratoria.backend.simulation.catalog;
import com.oratoria.backend.training.TrainingType;
import java.util.List;
public record SimulationScenario(String id,String title,String description,TrainingType trainingType,String aiRole,String context,String objective,List<String> rules,String openingMessage){ }
