CREATE TABLE public.binance_holdings
(
    datapoint_id integer NOT NULL,
    created_at timestamp without time zone,
    amount character varying(39) COLLATE pg_catalog."default",
    CONSTRAINT binance_holdings_pkey PRIMARY KEY (datapoint_id)
)