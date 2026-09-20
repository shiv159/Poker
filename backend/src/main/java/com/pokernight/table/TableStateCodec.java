package com.pokernight.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TableStateCodec {
  private final ObjectMapper json;
  public TableStateCodec(ObjectMapper json) { this.json=json; }
  public TableState read(String encoded) throws Exception { return json.readValue(encoded, TableState.class); }
  public String write(TableState state) throws Exception { return json.writeValueAsString(state); }
}
