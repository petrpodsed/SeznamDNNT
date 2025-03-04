import { Observable, Subject, BehaviorSubject, ReplaySubject } from 'rxjs';
import { Params, ParamMap } from '@angular/router';
import { Configuration, Sort } from './shared/configuration';
import { User } from './shared/user';
import { Filter } from './shared/filter';
import { Zadost } from './shared/zadost';
import { NavigationStore } from './shared/navigationstore';
import { NotifSettings } from './shared/notifsettings';
import { FacetsStore } from './shared/facetstore';

export class AppState {

  config: Configuration;

  private _paramsProcessed: ReplaySubject<string> = new ReplaySubject(3);
  public paramsProcessed: Observable<any> = this._paramsProcessed.asObservable();

  private _stateSubject = new Subject();
  public stateChanged: Observable<any> = this._stateSubject.asObservable();

  public _configSubject = new Subject();
  public configSubject: Observable<any> = this._configSubject.asObservable();

  private loggedSubject: Subject<boolean> = new Subject();
  public loggedChanged: Observable<boolean> = this.loggedSubject.asObservable();

  public currentLang: string;
  public activePage: string;
  public loading: boolean = true;

  public q: string;
  public page: number = 0;
  public rows: number = 20;

 
  // page,rows  
  public navigationstore: NavigationStore = new NavigationStore();
  // facets
  public facetsstore: FacetsStore = new FacetsStore();
  // prefix search 
  public prefixsearch :{[key: string]: string} = {};


  // store for sort
  public sort: {[key: string]: Sort} = {};

  //public prefix: {[key: string]: Sort} = {};

  // vyhodit ?? 
  public rokvydani:string;

  // Seznam stavu zaznamu pro uzivatel
  public user: User;
  public logged = false;
  public expirationDialog = false;


  public consent: boolean = true;

  public usedFilters: Filter[] = [];

  public fullCatalog: boolean;
  public withNotification: boolean;

  // Notifications filter
  public notificationSettings:NotifSettings = new NotifSettings();


  
  // Aktualni zadost kam se pridavaji navrhy; zmenit
  // NZN, VN, VN_, VNL (VNL a VNZ je typ na omezeni na terminal tedy jeden typ )
  // uzivatel ma jednu zadost na omezeni na terminal a rozhodne se podle ceho ji posle  
  //currentZadost: {VVS: Zadost, VVN: Zadost, NZN: Zadost} = {VVS: null, NZN: null, VVN:null};
  currentZadost: {NZN: Zadost,VN: Zadost, VNX: Zadost} = {
    VN: null,
    NZN: null, 
    VNX:null
  };

  setConfig(cfg: Configuration) {
    this.config = cfg;
    this.currentLang = cfg.lang;
  }


  processParams(searchParams: ParamMap, url: string) {

    let navigationKey = this.navigationstore.findKeyFromUrl(url);

    this.usedFilters = [];

    if (!this.sort['sort']) {
      this.sort['sort'] = this.config.sorts.sort[0];
    }
    if (!this.sort['sort_account']) {
      this.sort['sort_account'] = this.config.sorts.sort_account[0];
    }
    if (!this.sort['user_sort_account']) {
      this.sort['user_sort_account'] = this.config.sorts.user_sort_account[0];
    }
 

    this.fullCatalog = false;
    this.withNotification = false;
    this.notificationSettings.selected = null;

    searchParams.keys.forEach(p => {
      const param = searchParams.get(p);
      const params = searchParams.getAll(p);

      if (p === 'q') {
        this.q = param;
      } else if (p === 'rows') {
        this.rows = parseInt(param);
        if (navigationKey) {
          this.navigationstore.setRows(navigationKey, this.rows);
        }
      } else if (p === 'page') {
        this.page = parseInt(param);
        if (navigationKey) {
          this.navigationstore.setPage(navigationKey, this.page);
        }
      } else if (p === 'fullCatalog') {
        this.fullCatalog = param === 'true';
      } else if (p === 'withNotification') {
        this.withNotification = param === 'true';
      } else if (p === 'sort') {
        //this.sort = this.config.sorts.find(s => param === (s.field + " " + s.dir));
        this.sort.sort= this.config.sorts.sort.find(s => param === (s.field + " " + s.dir));
      } else if (p === 'sort_account') {
        //this.sort = this.config.sorts.find(s => param === (s.field + " " + s.dir));
        this.sort.sort_account = this.config.sorts.sort_account.find(s => param === (s.field + " " + s.dir));
      } else if (p === 'user_sort_account') {
        //this.sort = this.config.sorts.find(s => param === (s.field + " " + s.dir));
        this.sort.user_sort_account = this.config.sorts.user_sort_account.find(s => param === (s.field + " " + s.dir));
      } else if (p === 'notificationFilter') {

         let selectedNotification = this.notificationSettings.all.find(n=> n.id === param); 
         this.notificationSettings.selected = selectedNotification;

      } else {
        if (this.config.filterFields.includes(p)) {
          if (params.length>1) {
            params.forEach(op=> {
              this.usedFilters.push({field: p, value: op});
            });
          } else {
            this.usedFilters.push({field: p, value: param});
          }

        }
      }
    });
    this._paramsProcessed.next();
  }


  setNavigationStateUI() {

  }

  setLogged(res: any) {
    const changed = this.logged;
    if (res.error) {
      this.logged = false;
      this.user = null;
    } else {
      this.logged = true;
      this.user = res;
      if (res.zadost) {
        // res.zadost je Array max 3 elementy. Muze mit navrh=NZN nebo navrh=VVS 
        res.zadost.forEach(z => {
          if (z.navrh === 'NZN') {
            this.currentZadost.NZN = z;
          }else if (z.navrh === 'VN') {
            this.currentZadost.VN = z;
          }else if (z.navrh === 'VNZ' || z.navrh === 'VNL') {
            this.currentZadost.VNX = z;
          }
        }); 
        
      }

      if (res.notificationsettings) {
        this.notificationSettings = res.notificationsettings;
      }
    }
    this.loggedSubject.next(changed === this.logged);
  }


  isNavigationStoreKeyInitialized(key:string){ 
    return this.navigationstore.contains(key);    
  }

  initDefaultNavitionStoreKey(key:string) {
    this.navigationstore.initDefault(key);
  }
}
