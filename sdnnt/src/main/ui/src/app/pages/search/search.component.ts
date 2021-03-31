import { HttpParams } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Params } from '@angular/router';
import { AppService } from 'src/app/app.service';
import { AppState } from 'src/app/app.state';
import { SolrDocument } from 'src/app/shared/solr-document';
import { SolrResponse } from 'src/app/shared/solr-response';

@Component({
  selector: 'app-search',
  templateUrl: './search.component.html',
  styleUrls: ['./search.component.scss']
})
export class SearchComponent implements OnInit {

  loading: boolean;
  docs: SolrDocument[];
  searchResponse: SolrResponse;
  facets;
  numFound: number;

  constructor(
    private route: ActivatedRoute,
    private service: AppService,
    public state: AppState
    ) { }


  ngOnInit(): void {
    this.state.activePage = 'Search';
    this.route.queryParams.subscribe(val => {
      this.search(val);
    });
  }

  search(params: Params) {
    this.loading = true;
    const p = Object.assign({}, params);
    this.docs = [];
    this.searchResponse = null;
    this.facets = null;
    this.service.search(p as HttpParams).subscribe((resp: SolrResponse) => {
      this.searchResponse = resp;
      this.docs = resp.response.docs;
      this.numFound = resp.response.numFound;
      this.facets = resp.facet_counts.facet_fields;
      this.loading = false;
    });

  }

}
