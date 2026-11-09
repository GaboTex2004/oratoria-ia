--
-- PostgreSQL database dump
--


-- Dumped from database version 17.6 (Debian 17.6-2.pgdg13+1)
-- Dumped by pg_dump version 17.6 (Debian 17.6-2.pgdg13+1)




--
-- Name: app_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_users (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    display_name character varying(161) NOT NULL,
    email character varying(320) NOT NULL,
    enabled boolean NOT NULL,
    first_name character varying(80) NOT NULL,
    last_name character varying(80) NOT NULL,
    password_hash character varying(100) NOT NULL,
    role character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT app_users_role_check CHECK (((role)::text = ANY ((ARRAY['USER'::character varying, 'ADMIN'::character varying])::text[])))
);


--
-- Name: exercises; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exercises (
    id uuid NOT NULL,
    active boolean NOT NULL,
    code character varying(60) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(400) NOT NULL,
    prompt character varying(1000) NOT NULL,
    recommended_duration_seconds integer NOT NULL,
    title character varying(120) NOT NULL,
    training_type character varying(40) NOT NULL,
    CONSTRAINT exercises_training_type_check CHECK (((training_type)::text = ANY ((ARRAY['PRESENTATION'::character varying, 'INTERVIEW'::character varying, 'DEBATE'::character varying, 'SALES'::character varying, 'PROJECT_DEFENSE'::character varying, 'PITCH'::character varying, 'IMPROVISATION'::character varying])::text[])))
);


--
-- Name: plan_entitlements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plan_entitlements (
    id uuid NOT NULL,
    enabled boolean NOT NULL,
    feature character varying(60),
    limit_value integer,
    unlimited boolean NOT NULL,
    usage_limit character varying(60),
    plan_id uuid NOT NULL,
    CONSTRAINT plan_entitlements_feature_check CHECK (((feature)::text = ANY ((ARRAY['BASIC_VOICE_ANALYSIS'::character varying, 'ADVANCED_VOICE_ANALYSIS'::character varying, 'CAMERA_ANALYSIS'::character varying, 'ADVANCED_STATISTICS'::character varying, 'FULL_HISTORY'::character varying, 'SESSION_COMPARISON'::character varying, 'ADAPTIVE_TRAINING'::character varying, 'IMPROVISATION'::character varying, 'COMMUNICATION_STYLES'::character varying, 'UNLIMITED_SIMULATIONS'::character varying])::text[]))),
    CONSTRAINT plan_entitlements_usage_limit_check CHECK (((usage_limit)::text = ANY ((ARRAY['SESSIONS_PER_MONTH'::character varying, 'HISTORY_DAYS'::character varying, 'SIMULATIONS_PER_MONTH'::character varying])::text[])))
);


--
-- Name: plans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plans (
    id uuid NOT NULL,
    active boolean NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(100) NOT NULL,
    CONSTRAINT plans_code_check CHECK (((code)::text = ANY ((ARRAY['FREE'::character varying, 'PREMIUM'::character varying])::text[])))
);


--
-- Name: simulation_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.simulation_sessions (
    id uuid NOT NULL,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    current_turn integer NOT NULL,
    difficulty character varying(20) NOT NULL,
    error_code character varying(80),
    error_message character varying(500),
    final_feedback_json text,
    max_turns integer NOT NULL,
    overall_score double precision,
    scenario_id character varying(80) NOT NULL,
    status character varying(30) NOT NULL,
    sufficient_evidence boolean,
    training_type character varying(40) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT simulation_sessions_difficulty_check CHECK (((difficulty)::text = ANY ((ARRAY['BASIC'::character varying, 'INTERMEDIATE'::character varying, 'ADVANCED'::character varying])::text[]))),
    CONSTRAINT simulation_sessions_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'PROCESSING'::character varying, 'READY_TO_FINISH'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT simulation_sessions_training_type_check CHECK (((training_type)::text = ANY ((ARRAY['PRESENTATION'::character varying, 'INTERVIEW'::character varying, 'DEBATE'::character varying, 'SALES'::character varying, 'PROJECT_DEFENSE'::character varying, 'PITCH'::character varying, 'IMPROVISATION'::character varying])::text[])))
);


--
-- Name: simulation_turns; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.simulation_turns (
    id uuid NOT NULL,
    audio_duration_seconds integer,
    audio_mime_type character varying(100),
    audio_original_filename character varying(255),
    audio_size_bytes bigint,
    audio_storage_path character varying(500),
    created_at timestamp(6) with time zone NOT NULL,
    error_code character varying(80),
    error_message character varying(500),
    idempotency_key character varying(100),
    sequence_number integer NOT NULL,
    speaker character varying(10) NOT NULL,
    status character varying(20) NOT NULL,
    text text,
    simulation_id uuid NOT NULL,
    CONSTRAINT simulation_turns_speaker_check CHECK (((speaker)::text = ANY ((ARRAY['USER'::character varying, 'AI'::character varying])::text[]))),
    CONSTRAINT simulation_turns_status_check CHECK (((status)::text = ANY ((ARRAY['PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: speech_analyses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.speech_analyses (
    id uuid NOT NULL,
    attempt_count integer NOT NULL,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    error_code character varying(80),
    error_message character varying(500),
    estimated_pause_count integer,
    estimated_spoken_duration_seconds double precision,
    filler_counts_json text,
    filler_words_total integer,
    language character varying(16),
    longest_pause_seconds double precision,
    model character varying(80),
    provider character varying(40),
    recording_duration_seconds double precision,
    retryable boolean NOT NULL,
    schema_version character varying(16) NOT NULL,
    segments_json text,
    spoken_time_ratio double precision,
    spoken_words_per_minute double precision,
    started_at timestamp(6) with time zone,
    status character varying(30) NOT NULL,
    timestamps_available boolean,
    total_pause_duration_seconds double precision,
    transcript_text text,
    updated_at timestamp(6) with time zone NOT NULL,
    word_count integer,
    words_json text,
    words_per_minute double precision,
    session_id uuid NOT NULL,
    CONSTRAINT speech_analyses_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: speech_evaluations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.speech_evaluations (
    id uuid NOT NULL,
    areas_for_improvement_json text,
    attempt_count integer NOT NULL,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    criteria_scores_json text,
    error_code character varying(60),
    error_message character varying(500),
    executive_summary text,
    improved_examples_json text,
    insufficient_evidence_reason text,
    measured_indicators_json text,
    model character varying(80),
    overall_score double precision,
    provider character varying(40),
    recommendations_json text,
    retryable boolean NOT NULL,
    rubric_version character varying(60),
    schema_version character varying(16) NOT NULL,
    started_at timestamp(6) with time zone,
    status character varying(30) NOT NULL,
    strengths_json text,
    sufficient_evidence boolean,
    updated_at timestamp(6) with time zone NOT NULL,
    session_id uuid NOT NULL,
    CONSTRAINT speech_evaluations_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscriptions (
    id uuid NOT NULL,
    ends_at timestamp(6) with time zone,
    starts_at timestamp(6) with time zone NOT NULL,
    status character varying(30) NOT NULL,
    plan_id uuid NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT subscriptions_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'CANCELED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: training_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.training_sessions (
    id uuid NOT NULL,
    audio_mime_type character varying(100),
    audio_original_filename character varying(255),
    audio_size_bytes bigint,
    audio_storage_path character varying(500),
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    duration_seconds integer,
    overall_score double precision,
    status character varying(30) NOT NULL,
    training_type character varying(40) NOT NULL,
    exercise_id uuid,
    user_id uuid NOT NULL,
    CONSTRAINT training_sessions_status_check CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'RECORDING'::character varying, 'UPLOADED'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT training_sessions_training_type_check CHECK (((training_type)::text = ANY ((ARRAY['PRESENTATION'::character varying, 'INTERVIEW'::character varying, 'DEBATE'::character varying, 'SALES'::character varying, 'PROJECT_DEFENSE'::character varying, 'PITCH'::character varying, 'IMPROVISATION'::character varying])::text[])))
);


--
-- Name: app_users app_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_users
    ADD CONSTRAINT app_users_pkey PRIMARY KEY (id);


--
-- Name: exercises exercises_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exercises
    ADD CONSTRAINT exercises_pkey PRIMARY KEY (id);


--
-- Name: plan_entitlements plan_entitlements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_entitlements
    ADD CONSTRAINT plan_entitlements_pkey PRIMARY KEY (id);


--
-- Name: plans plans_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plans
    ADD CONSTRAINT plans_pkey PRIMARY KEY (id);


--
-- Name: simulation_sessions simulation_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simulation_sessions
    ADD CONSTRAINT simulation_sessions_pkey PRIMARY KEY (id);


--
-- Name: simulation_turns simulation_turns_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simulation_turns
    ADD CONSTRAINT simulation_turns_pkey PRIMARY KEY (id);


--
-- Name: speech_analyses speech_analyses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.speech_analyses
    ADD CONSTRAINT speech_analyses_pkey PRIMARY KEY (id);


--
-- Name: speech_evaluations speech_evaluations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.speech_evaluations
    ADD CONSTRAINT speech_evaluations_pkey PRIMARY KEY (id);


--
-- Name: subscriptions subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_pkey PRIMARY KEY (id);


--
-- Name: training_sessions training_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_sessions
    ADD CONSTRAINT training_sessions_pkey PRIMARY KEY (id);


--
-- Name: app_users uk4vj92ux8a2eehds1mdvmks473; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_users
    ADD CONSTRAINT uk4vj92ux8a2eehds1mdvmks473 UNIQUE (email);


--
-- Name: plan_entitlements uk_plan_entitlement_feature; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_entitlements
    ADD CONSTRAINT uk_plan_entitlement_feature UNIQUE (plan_id, feature);


--
-- Name: plan_entitlements uk_plan_entitlement_limit; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_entitlements
    ADD CONSTRAINT uk_plan_entitlement_limit UNIQUE (plan_id, usage_limit);


--
-- Name: simulation_turns uk_sim_turn_idempotency; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simulation_turns
    ADD CONSTRAINT uk_sim_turn_idempotency UNIQUE (simulation_id, idempotency_key);


--
-- Name: simulation_turns uk_sim_turn_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simulation_turns
    ADD CONSTRAINT uk_sim_turn_sequence UNIQUE (simulation_id, sequence_number);


--
-- Name: plans ukbsiq2g7uq9l49v27bsijicsys; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plans
    ADD CONSTRAINT ukbsiq2g7uq9l49v27bsijicsys UNIQUE (code);


--
-- Name: exercises ukd6m9u9wmsy3rffnwobuknddww; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exercises
    ADD CONSTRAINT ukd6m9u9wmsy3rffnwobuknddww UNIQUE (code);


--
-- Name: speech_analyses ukg50dsytnfnrmeq3in9dlwds07; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.speech_analyses
    ADD CONSTRAINT ukg50dsytnfnrmeq3in9dlwds07 UNIQUE (session_id);


--
-- Name: speech_evaluations ukt072asc4ilae6g1tmdil211xq; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.speech_evaluations
    ADD CONSTRAINT ukt072asc4ilae6g1tmdil211xq UNIQUE (session_id);


--
-- Name: subscriptions fk1n4vskik87od4wdk9xgvewuxe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT fk1n4vskik87od4wdk9xgvewuxe FOREIGN KEY (user_id) REFERENCES public.app_users(id);


--
-- Name: speech_analyses fk48geer932rfjxw50qwl857pa7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.speech_analyses
    ADD CONSTRAINT fk48geer932rfjxw50qwl857pa7 FOREIGN KEY (session_id) REFERENCES public.training_sessions(id);


--
-- Name: training_sessions fk9o5m8oviw775tx8m3svo0o8u3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_sessions
    ADD CONSTRAINT fk9o5m8oviw775tx8m3svo0o8u3 FOREIGN KEY (user_id) REFERENCES public.app_users(id);


--
-- Name: subscriptions fkb1uf5qnxi6uj95se8ykydntl1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT fkb1uf5qnxi6uj95se8ykydntl1 FOREIGN KEY (plan_id) REFERENCES public.plans(id);


--
-- Name: simulation_turns fkbbo5ccnfwefbcbk8d5jv076gg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simulation_turns
    ADD CONSTRAINT fkbbo5ccnfwefbcbk8d5jv076gg FOREIGN KEY (simulation_id) REFERENCES public.simulation_sessions(id);


--
-- Name: plan_entitlements fkblnefun3vyc44hps0lh0hyymh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_entitlements
    ADD CONSTRAINT fkblnefun3vyc44hps0lh0hyymh FOREIGN KEY (plan_id) REFERENCES public.plans(id);


--
-- Name: simulation_sessions fkom6dxvyh8pjsqhffljo1mnrd2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simulation_sessions
    ADD CONSTRAINT fkom6dxvyh8pjsqhffljo1mnrd2 FOREIGN KEY (user_id) REFERENCES public.app_users(id);


--
-- Name: training_sessions fktj5cggbclt2llahih4cjwhwgs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_sessions
    ADD CONSTRAINT fktj5cggbclt2llahih4cjwhwgs FOREIGN KEY (exercise_id) REFERENCES public.exercises(id);


--
-- Name: speech_evaluations fktp9o2pebxx2pirje69cv30nl9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.speech_evaluations
    ADD CONSTRAINT fktp9o2pebxx2pirje69cv30nl9 FOREIGN KEY (session_id) REFERENCES public.training_sessions(id);


--
-- PostgreSQL database dump complete
--


