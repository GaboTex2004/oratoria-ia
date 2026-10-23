package com.oratoria.backend.simulation.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="simulation_turns", uniqueConstraints={
    @UniqueConstraint(name="uk_sim_turn_sequence", columnNames={"simulation_id","sequence_number"}),
    @UniqueConstraint(name="uk_sim_turn_idempotency", columnNames={"simulation_id","idempotency_key"})})
public class SimulationTurn {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="simulation_id",nullable=false) private SimulationSession simulation;
    @Column(name="sequence_number",nullable=false) private int sequenceNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private SimulationSpeaker speaker;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private SimulationTurnStatus status;
    @Column(columnDefinition="TEXT") private String text;
    @Column(name="idempotency_key",length=100) private String idempotencyKey;
    @Column(name="audio_storage_path",length=500) private String audioStoragePath;
    @Column(name="audio_mime_type",length=100) private String audioMimeType;
    @Column(name="audio_size_bytes") private Long audioSizeBytes;
    @Column(name="audio_original_filename",length=255) private String audioOriginalFilename;
    @Column(name="audio_duration_seconds") private Integer audioDurationSeconds;
    @Column(name="error_code",length=80) private String errorCode;
    @Column(name="error_message",length=500) private String errorMessage;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();

    protected SimulationTurn(){}
    public static SimulationTurn ai(SimulationSession s,int sequence,String text){ return new SimulationTurn(s,sequence,SimulationSpeaker.AI,SimulationTurnStatus.COMPLETED,text,null); }
    public static SimulationTurn userProcessing(SimulationSession s,int sequence,String key){ return new SimulationTurn(s,sequence,SimulationSpeaker.USER,SimulationTurnStatus.PROCESSING,null,key); }
    private SimulationTurn(SimulationSession s,int seq,SimulationSpeaker speaker,SimulationTurnStatus status,String text,String key){simulation=s;sequenceNumber=seq;this.speaker=speaker;this.status=status;this.text=text;idempotencyKey=key;}
    public void attachAudio(String path,String mime,long size,String filename,int duration){audioStoragePath=path;audioMimeType=mime;audioSizeBytes=size;audioOriginalFilename=filename;audioDurationSeconds=duration;}
    public void attachTranscript(String transcript){text=transcript;}
    public void retry(){status=SimulationTurnStatus.PROCESSING;errorCode=null;errorMessage=null;}
    public void complete(String transcript){text=transcript;status=SimulationTurnStatus.COMPLETED;errorCode=null;errorMessage=null;}
    public void fail(String code,String message){status=SimulationTurnStatus.FAILED;errorCode=code;errorMessage=message;}
    public boolean hasAudio(){return audioStoragePath!=null&&!audioStoragePath.isBlank();}
    public UUID getId(){return id;} public SimulationSession getSimulation(){return simulation;} public int getSequenceNumber(){return sequenceNumber;} public SimulationSpeaker getSpeaker(){return speaker;} public SimulationTurnStatus getStatus(){return status;} public String getText(){return text;} public String getIdempotencyKey(){return idempotencyKey;} public String getAudioStoragePath(){return audioStoragePath;} public String getAudioMimeType(){return audioMimeType;} public Long getAudioSizeBytes(){return audioSizeBytes;} public String getAudioOriginalFilename(){return audioOriginalFilename;} public Integer getAudioDurationSeconds(){return audioDurationSeconds;} public String getErrorCode(){return errorCode;} public String getErrorMessage(){return errorMessage;} public Instant getCreatedAt(){return createdAt;}
}
