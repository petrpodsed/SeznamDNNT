import { SolrDocument } from 'src/app/shared/solr-document';

export class User {
  username: string;
  pwd: string;
  role: string;
  isActive: boolean;
  notifikace_interval:string;
  //isApiEnabled:boolean;

  typ: string; //pravnicka/fyzicka osoba
  titul: string;
  jmeno: string;
  prijmeni: string;
  ico: string;
  adresa: string;
  psc: string;
  mesto: string;
  telefon: string;
  email: string;
  kontaktni: string;
  apikey: string;

  institution: string;


  nositel: string[]; // Nositel autorských práv k dílu: 
  poznamka: string;
  error?: string;


}

