import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { IonicModule } from '@ionic/angular';
import { AppComponent } from './app/app.component';
import { importProvidersFrom } from '@angular/core';

bootstrapApplication(AppComponent, { providers: [provideHttpClient(), provideAnimationsAsync(), importProvidersFrom(IonicModule.forRoot())] }).catch(console.error);
