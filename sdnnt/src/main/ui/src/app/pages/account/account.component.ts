import { HttpParams } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { AppService } from 'src/app/app.service';
import { AppState } from 'src/app/app.state';
import { DialogSendRequestComponent } from 'src/app/components/dialog-send-request/dialog-send-request.component';
import { SolrDocument } from 'src/app/shared/solr-document';
import { SolrResponse } from 'src/app/shared/solr-response';
import { Zadost } from 'src/app/shared/zadost';


@Component({
  selector: 'app-account',
  templateUrl: './account.component.html',
  styleUrls: ['./account.component.scss']
})
export class AccountComponent implements OnInit {

  filterState = [
    {id: "open", val: "neodeslano"},
    {id: "waiting", val: "ceka_na_posouzeni"},
    {id: "processed", val: "zpracovano"}
  ];

  filterType = [
    {id: "NZN", val: "navrzeno_na_zarazeni"},
    {id: "VVS", val: "navrzeno_na_vyrazeni"}
  ];
  
  loading: boolean;
  items: SolrDocument[];
  searchResponse: SolrResponse;
  facets;
  numFound: number;

  displayedColumns = ['datum_zadani','user', 'institution', 'state', 'navrh','datum_vyrizeni','count', 'pozadavek','poznamka','actions'];
  zadosti: Zadost[] = [];

  stateFilter: string;
  newStavFilter: string;
  institutionFilter:string;
  delegatedFilter: string;
  priorityFilter: string;


  allResultInstitutions:string[] = [];
  allPriorities:string[] = [];
  allDelegated:string[] = [];


  constructor(
    public dialog: MatDialog,
    private route: ActivatedRoute,
    private router: Router,
    private service: AppService,
    public state: AppState
    ) { }


  ngOnInit(): void {
    if (!this.state.user) {
      this.router.navigate(['/']);
      return;
    }
    this.state.activePage = 'Account';
    this.route.queryParams.subscribe(val => {
      this.search(val);
      this.newStavFilter = val.navrh;
      this.stateFilter = val.state;
    });
  }

  search(params: Params) {
    this.loading = true;
    const p = Object.assign({}, params);

    this.items = [];
    this.searchResponse = null;
    this.facets = null;
    this.service.searchAccount(p as HttpParams).subscribe((resp: any) => {
      if (!resp.error) {
        this.zadosti = resp.response.docs;
        this.numFound = resp.response.numFound;
        this.loading = false;

        this.allResultInstitutions = resp.facet_counts.facet_fields.institution.filter((itm) => itm.value > 0 ).map( function(val, index){
            return val.name;
        });

        this.allPriorities = resp.facet_counts.facet_fields.priority.filter((itm) => itm.value > 0 ).map( function(val, index){
          return val.name;
        });

        this.allDelegated = resp.facet_counts.facet_fields.delegated.filter((itm) => itm.value > 0 ).map( function(val, index){
          return val.name;
        });

      }
    });

  }

  setStav(navrh: string) {
    const q: any = {};
    // added by peter
    if (this.newStavFilter === navrh) {
      q.navrh = null;
    } else {
      q.navrh = navrh;
    }
    

    q.page = null;
    this.router.navigate([], { queryParams: q, queryParamsHandling: 'merge' });
  }


  setStavZadosti(state: string) {
    const q: any = {};
    if (this.stateFilter === state) {
      q.state = null;
    } else {
      q.state = state;
    }
    
    q.page = null;
    this.router.navigate([], { queryParams: q, queryParamsHandling: 'merge' });
  }

  setInstitution(institution: string) {
    const q: any = {};
    q.institution = institution;
    if (this.institutionFilter === institution) {
      q.institution = null;
      this.institutionFilter = null;
    } else {
      this.institutionFilter  = institution;
      q.institution = institution;
    }
    //this.allResultInstitutions = this.allResultInstitutions.filter(obj => obj !== institution);
    q.page = null;
    this.router.navigate([], { queryParams: q, queryParamsHandling: 'merge' });
  }


  setDelegated(delegated: string) {
    const q: any = {};
    q.delegated = delegated;
    if (this.delegatedFilter === delegated) {
      q.delegated = null;
      this.delegatedFilter = null;
    } else {
      q.delegated = delegated;
      this.delegatedFilter  = delegated;
    }
    q.page = null;
    this.router.navigate([], { queryParams: q, queryParamsHandling: 'merge' });
  }

  setPriority(priority: string) {
    const q: any = {};
    q.priority = priority;
    if (this.priorityFilter === priority) {
      q.priority = null;
      this.priorityFilter = null;
    } else {
      q.priority = priority;
      this.priorityFilter  = priority;
    }
    q.page = null;
    this.router.navigate([], { queryParams: q, queryParamsHandling: 'merge' });
  }

  removeAllFilters() {
    this.stateFilter = null;
    this.newStavFilter = null;
    this.institutionFilter = null; 
    this.priorityFilter = null;
    this.delegatedFilter = null;

    const q: any = {};
    q.navrh = null;
    q.state = null;
    q.institution = null;
    q.priority = null;
    q.delegated = null;
    q.page = null;

    this.router.navigate([], { queryParams: q, queryParamsHandling: 'merge' });
  }

  showRecords(zadost: Zadost) {
    // const dialogRef = this.dialog.open(ZadostInfoDialogComponent, {
    //   width: '1150px',
    //   data: zadost,
    //   panelClass: 'app-states-dialog'
    // });
    this.router.navigate(['zadost', zadost.id], {});
  }

  send(zadost: Zadost) {
    // no identifiers
    if (zadost.identifiers) {
      const dialogRef = this.dialog.open(DialogSendRequestComponent, {
        width: '750px',
        data: zadost,
        panelClass: 'app-send-request'
      });
    }

  }

  countProcessed(zadost: Zadost) {
    return Object.keys(zadost.process).length;
  }

  process(zadost: Zadost) {

    if (zadost.identifiers.length > Object.keys(zadost.process).length) {
      this.service.showSnackBar('alert.oznaceni_jako_zpracovane_error', 'desc.ne_vsechny_zaznamy_jsou_zpracovane', true);
      return;
    }
    this.service.processZadost(zadost).subscribe(res => {
      if (res.error) {
        this.service.showSnackBar('alert.ulozeni_zadosti_error', res.error, true);
      } else {
        this.service.showSnackBar('alert.ulozeni_zadosti_success', '', false);
        zadost.state = 'processed';
      }
    });
  }


}
