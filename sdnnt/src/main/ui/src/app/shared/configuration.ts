import { User } from "./user";

export interface Sort { label: string; field: string; dir: string};

export interface Configuration {
  context: string;
  lang: string;
  snackDuration: number;
  homeTabs: string[];

  // Seznam roli
  role: string[];
  
  // Seznam stavu zaznamu pro role
  dntStates: {[role: string]: string[]};

  // Seznam poli identifikatoru
  identifiers: string[];

  // Seznam poli, ktere se zpracuju v url jako filter
  filterFields: string[];
}
